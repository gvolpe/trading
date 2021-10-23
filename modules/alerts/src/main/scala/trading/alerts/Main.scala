package trading.alerts

import trading.core.AppTopic
import trading.core.http.Ember
import trading.core.snapshots.SnapshotReader
import trading.domain.Alert
import trading.events.TradeEvent
import trading.lib.{ Consumer, Producer }

import cats.effect.*
import dev.profunktor.pulsar.{ Pulsar, Subscription }
import dev.profunktor.pulsar.schema.circe.bytes.*
import dev.profunktor.redis4cats.effect.Log.Stdout.*
import fs2.Stream

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { (server, engine) =>
        Stream.eval(server.useForever).concurrently(engine.run)
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
      _      <- Resource.eval(IO.println(">>> Initializing alerts service <<<"))
      alertsTopic = AppTopic.Alerts.make(config.pulsar)
      eventsTopic = AppTopic.TradingEvents.make(config.pulsar)
      snapshots <- SnapshotReader.make[IO](config.redisUri)
      producer  <- Producer.pulsar[IO, Alert](pulsar, alertsTopic)
      consumer  <- Consumer.pulsar[IO, TradeEvent](pulsar, eventsTopic, sub)
      server = Ember.default[IO]
    yield server -> AlertEngine.make[IO](consumer, producer, snapshots)
