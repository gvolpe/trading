package trading.snapshots

import scala.concurrent.duration.*

import trading.core.http.Ember
import trading.core.snapshots.{ SnapshotReader, SnapshotWriter }
import trading.core.{ AppTopic, TradeEngine }
import trading.domain.AppId
import trading.events.{ SwitchEvent, TradeEvent }
import trading.lib.{ given, * }
import trading.lib.Consumer.{ Msg, MsgId }
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
      .flatMap { (server, distLockRes, trConsumer, swConsumer, reader, fsm) =>
        Stream.eval(server.useForever).concurrently {
          Stream.resource(distLockRes).flatMap { distLock =>
            val ticks: Stream[IO, Engine.In] =
              Stream.fixedDelay[IO](2.seconds).evalMap(_ => distLock.refresh)

            Stream.eval(IO.deferred[Unit]).flatMap { gate =>
              def process(st: TradeState, trading: Stream[IO, Msg[TradeEvent]]) =
                trading
                  .either(Stream.exec(gate.get) ++ swConsumer.receiveM)
                  .merge(ticks)
                  .evalMapAccumulate(st -> List.empty)(fsm.run)

              Stream.eval(reader.latest).flatMap {
                case Some(st, id) =>
                  process(st, trConsumer.rewind(id, gate))
                case None =>
                  process(TradeState.empty, trConsumer.rewind(MsgId.earliest, gate))
              }
            }
          }
        }
      }
      .compile
      .drain

  // TradeEvent subscription: one per instance
  def trSub(appId: AppId) =
    Subscription.Builder
      .withName(appId.show)
      .withType(Subscription.Type.Exclusive)
      .withMode(Subscription.Mode.NonDurable)
      .build

  // SwitchEvent subscription: one per instance (could also be failover, though)
  def swSub(appId: AppId) =
    Subscription.Builder
      .withName(appId.show)
      .withType(Subscription.Type.Exclusive)
      .withMode(Subscription.Mode.NonDurable)
      .build

  val compact =
    PulsarConsumer.Settings[IO, SwitchEvent]().withReadCompacted.some

  def resources =
    for
      config <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.make[IO](config.pulsar.url)
      _      <- Resource.eval(Logger[IO].info("Initializing snapshots service"))
      teTopic = AppTopic.TradingEvents.make(config.pulsar)
      swTopic = AppTopic.SwitchEvents.make(config.pulsar)
      redis <- RedisClient[IO].from(config.redisUri.value)
      distLock = DistLock.make[IO]("snap-lock", config.appId, redis)
      reader     <- SnapshotReader.fromClient[IO](redis)
      writer     <- SnapshotWriter.fromClient[IO](redis, config.keyExpiration)
      trConsumer <- Consumer.pulsar[IO, TradeEvent](pulsar, teTopic, trSub(config.appId))
      swConsumer <- Consumer.pulsar[IO, SwitchEvent](pulsar, swTopic, swSub(config.appId), compact)
      fsm    = Engine.fsm(trConsumer, swConsumer, writer)
      server = Ember.default[IO](config.httpPort)
    yield (server, distLock, trConsumer, swConsumer, reader, fsm)
