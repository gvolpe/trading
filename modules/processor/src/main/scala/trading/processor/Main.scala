package trading.processor

import trading.commands.TradeCommand
import trading.core.AppTopic
import trading.core.snapshots.SnapshotReader
import trading.events.TradeEvent
import trading.lib.inject._
import trading.lib.{ Consumer, Producer }

import cats.effect._
import dev.profunktor.pulsar.{ Config, Pulsar, Subscription }
import dev.profunktor.redis4cats.effect.Log.Stdout._
import fs2.Stream

object Main extends IOApp.Simple {

  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap(_.run)
      .compile
      .drain

  val config = Config.Builder.default

  val cmdTopic    = AppTopic.TradingCommands.make(config)
  val eventsTopic = AppTopic.TradingEvents.make(config)

  val sub =
    Subscription.Builder
      .withName("trading-app")
      .withType(Subscription.Type.KeyShared)
      .build

  def resources =
    for {
      pulsar    <- Pulsar.make[IO](config.url)
      _         <- Resource.eval(IO.println(">>> Initializing processor service <<<"))
      producer  <- Producer.pulsar[IO, TradeEvent](pulsar, eventsTopic)
      snapshots <- SnapshotReader.make[IO]
      consumer  <- Consumer.pulsar[IO, TradeCommand](pulsar, cmdTopic, sub)
    } yield Engine.make(consumer, producer, snapshots)

}
