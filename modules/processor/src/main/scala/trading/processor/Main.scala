package trading.processor

import trading.commands.TradeCommand
import trading.core.AppTopic
import trading.core.http.Ember
import trading.core.snapshots.SnapshotReader
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
      .withName("processor")
      .withType(Subscription.Type.KeyShared)
      .build

  def resources =
    for
      config <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.make[IO](config.pulsar.url)
      _      <- Resource.eval(IO.println(">>> Initializing processor service <<<"))
      cmdTopic    = AppTopic.TradingCommands.make(config.pulsar)
      eventsTopic = AppTopic.TradingEvents.make(config.pulsar)
      producer  <- Producer.pulsar[IO, TradeEvent](pulsar, eventsTopic)
      snapshots <- SnapshotReader.make[IO](config.redisUri)
      consumer  <- Consumer.pulsar[IO, TradeCommand](pulsar, cmdTopic, sub)
      server = Ember.default[IO]
    yield server -> Engine.make(consumer, producer, snapshots)
