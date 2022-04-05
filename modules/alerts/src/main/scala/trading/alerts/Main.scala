package trading.alerts

import trading.core.AppTopic
import trading.core.http.Ember
import trading.core.snapshots.SnapshotReader
import trading.domain.Alert
import trading.events.TradeEvent
import trading.lib.{ given, * }
import trading.state.TradeState

import cats.effect.*
import cats.syntax.all.*
import dev.profunktor.pulsar.{ Producer as PulsarProducer, Pulsar, SeqIdMaker, Subscription }
import fs2.Stream

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { (server, consumer, snapshots, fsm) =>
        Stream.eval(server.useForever).concurrently {
          val st = TradeState.empty
          Stream.eval(snapshots.getLastId).flatMap {
            case Some(id) =>
              consumer.receiveM(id).evalMapAccumulate(st)(fsm.run)
            case None =>
              consumer.receiveM.evalMapAccumulate(st)(fsm.run)
          }
        }
      }
      .compile
      .drain

  // sharded by symbol (see Shard[TradeEvent] instance)
  val sub =
    Subscription.Builder
      .withName("alerts")
      .withType(Subscription.Type.KeyShared)
      .build

  // Alert producer settings, dedup and partitioned (for topic compaction in WS)
  val pSettings =
    PulsarProducer
      .Settings[IO, Alert]()
      .withDeduplication(SeqIdMaker.fromEq)
      .withMessageKey(Partition[Alert].key)
      .some

  def resources =
    for
      config <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.make[IO](config.pulsar.url)
      _      <- Resource.eval(Logger[IO].info("Initializing alerts service"))
      alertsTopic = AppTopic.Alerts.make(config.pulsar)
      eventsTopic = AppTopic.TradingEvents.make(config.pulsar)
      snapshots <- SnapshotReader.make[IO](config.redisUri)
      producer  <- Producer.pulsar[IO, Alert](pulsar, alertsTopic, pSettings)
      consumer  <- Consumer.pulsar[IO, TradeEvent](pulsar, eventsTopic, sub)
      server = Ember.default[IO](config.httpPort)
    yield (server, consumer, snapshots, Engine.fsm(producer, consumer.ack))
