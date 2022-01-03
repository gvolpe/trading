package trading.alerts

import trading.core.AppTopic
import trading.core.http.Ember
import trading.core.snapshots.SnapshotReader
import trading.domain.Alert
import trading.events.TradeEvent
import trading.lib.{ given, * }
import trading.state.{ DedupState, TradeState }

import cats.effect.*
import dev.profunktor.pulsar.{ Pulsar, Subscription }
import fs2.Stream

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { (server, consumer, snapshots, fsm) =>
        Stream.eval(server.useForever).concurrently {
          Stream.eval(snapshots.latest).flatMap { maybeSt =>
            consumer.receiveM.evalMapAccumulate(
              // could use DedupRegistry here as we do in processor
              maybeSt.getOrElse(TradeState.empty) -> DedupState.empty
            )(fsm.run)
          }
        }
      }
      .compile
      .drain

  val sub =
    Subscription.Builder
      .withName("alerts")
      .withType(Subscription.Type.Shared)
      .build

  def resources =
    for
      config <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.make[IO](config.pulsar.url)
      _      <- Resource.eval(Logger[IO].info("Initializing alerts service"))
      alertsTopic = AppTopic.Alerts.make(config.pulsar)
      eventsTopic = AppTopic.TradingEvents.make(config.pulsar)
      snapshots <- SnapshotReader.make[IO](config.redisUri)
      producer  <- Producer.pulsar[IO, Alert](pulsar, alertsTopic)
      consumer  <- Consumer.pulsar[IO, TradeEvent](pulsar, eventsTopic, sub)
      server = Ember.default[IO](config.httpPort)
    yield (server, consumer, snapshots, Engine.fsm(producer, consumer.ack))
