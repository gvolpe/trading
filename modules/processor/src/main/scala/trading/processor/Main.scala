package trading.processor

import trading.commands.TradeCommand
import trading.core.AppTopic
import trading.core.http.Ember
import trading.core.snapshots.SnapshotReader
import trading.events.TradeEvent
import trading.lib.{ given, * }
import trading.state.TradeState

import cats.effect.*
import cats.syntax.all.*
import dev.profunktor.pulsar.{ Consumer as PulsarConsumer, Producer as PulsarProducer, Pulsar, Subscription }
import fs2.Stream
import dev.profunktor.pulsar.SeqIdMaker
import java.util.UUID

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { (server, consumer, events, snapshots, fsm) =>
        Stream.eval(server.useForever).concurrently {
          Stream.eval(snapshots.latest).flatMap {
            case Some(st, id) =>
              val log = Logger[IO].debug(s"Last ID: $id, Status: ${st.status}")
              val src = consumer.receiveM(id).merge[IO, Engine.In](events)
              Stream.eval(log) ++ src.evalMapAccumulate(st)(fsm.run)
            case None =>
              val src = consumer.receiveM.merge[IO, Engine.In](events)
              src.evalMapAccumulate(TradeState.empty)(fsm.run)
          }
        }
      }
      .compile
      .drain

  // sharded by symbol (see Shard[TradeCommand] instance)
  val cmdSub =
    Subscription.Builder
      .withName("processor")
      .withType(Subscription.Type.KeyShared)
      .build

  def swtSub(id: UUID) =
    Subscription.Builder
      .withName(s"processor-${id.show}")
      .withType(Subscription.Type.Exclusive)
      .build

  val compact =
    PulsarConsumer.Settings[IO, TradeEvent.Switch]().withReadCompacted.some

  val evtSettings =
    PulsarProducer
      .Settings[IO, TradeEvent]()
      .withDeduplication(SeqIdMaker.fromEq)
      .withShardKey(Shard[TradeEvent].key)
      .some

  val swtSettings =
    PulsarProducer
      .Settings[IO, TradeEvent.Switch]()
      .withDeduplication(SeqIdMaker.fromEq)
      .withMessageKey(Partition[TradeEvent.Switch].key)
      .some

  def resources =
    for
      config <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.make[IO](config.pulsar.url)
      uuid   <- Resource.eval(GenUUID[IO].make[UUID])
      _      <- Resource.eval(Logger[IO].info(s"Initializing processor service: ${uuid.show}"))
      server   = Ember.default[IO](config.httpPort)
      cmdTopic = AppTopic.TradingCommands.make(config.pulsar)
      evtTopic = AppTopic.TradingEvents.make(config.pulsar)
      swtTopic = AppTopic.SwitchEvents.make(config.pulsar)
      producer  <- Producer.pulsar[IO, TradeEvent](pulsar, evtTopic, evtSettings)
      switcher  <- Producer.pulsar[IO, TradeEvent.Switch](pulsar, swtTopic, swtSettings)
      snapshots <- SnapshotReader.make[IO](config.redisUri)
      consumer  <- Consumer.pulsar[IO, TradeCommand](pulsar, cmdTopic, cmdSub)
      events    <- Consumer.pulsar[IO, TradeEvent.Switch](pulsar, evtTopic, swtSub(uuid), compact).map(_.receive)
    yield (server, consumer, events, snapshots, Engine.fsm(producer, switcher, consumer.ack))
