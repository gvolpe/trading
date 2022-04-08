package trading.snapshots

import scala.concurrent.duration.*

import trading.core.http.Ember
import trading.core.snapshots.{ SnapshotReader, SnapshotWriter }
import trading.core.{ AppTopic, TradeEngine }
import trading.events.{ SwitchEvent, TradeEvent }
import trading.lib.{ given, * }
import trading.state.TradeState

import cats.effect.*
import cats.syntax.all.*
import dev.profunktor.pulsar.{ Consumer as PulsarConsumer, Pulsar, Subscription }
import dev.profunktor.redis4cats.connection.RedisClient
import fs2.Stream

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { (server, trConsumer, swConsumer, reader, fsm) =>
        val ticks: Stream[IO, Engine.In] =
          Stream.fixedDelay[IO](2.seconds)

        Stream.eval(server.useForever).concurrently {
          Stream.eval(reader.latest).flatMap {
            case Some(st, id) =>
              Stream.exec(Logger[IO].debug(s"SNAPSHOTS: $st")) ++
                trConsumer
                  .receiveM(id)
                  .either(swConsumer.receiveM)
                  .merge(ticks)
                  .evalMapAccumulate(st -> List.empty)(fsm.run)
            case None =>
              trConsumer
                .receiveM
                .either(swConsumer.receiveM)
                .merge(ticks)
                .evalMapAccumulate(TradeState.empty -> List.empty)(fsm.run)
          }
        }
      }
      .compile
      .drain

  // Failover subscription (it's enough to deploy two instances)
  val sub =
    Subscription.Builder
      .withName("snapshots")
      .withType(Subscription.Type.Failover)
      .build

  val compact =
    PulsarConsumer.Settings[IO, SwitchEvent]().withReadCompacted.some

  def resources =
    for
      config <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.make[IO](config.pulsar.url)
      _      <- Resource.eval(Logger[IO].info("Initializing snapshots service"))
      topic = AppTopic.TradingEvents.make(config.pulsar)
      redis      <- RedisClient[IO].from(config.redisUri.value)
      reader     <- SnapshotReader.fromClient[IO](redis)
      writer     <- SnapshotWriter.fromClient[IO](redis, config.keyExpiration)
      trConsumer <- Consumer.pulsar[IO, TradeEvent](pulsar, topic, sub)
      swConsumer <- Consumer.pulsar[IO, SwitchEvent](pulsar, topic, sub, compact)
      fsm    = Engine.fsm(trConsumer, swConsumer, writer)
      server = Ember.default[IO](config.httpPort)
    yield (server, trConsumer, swConsumer, reader, fsm)
