package trading

import trading.commands.TradeCommand
import trading.core.AppTopic
import trading.core.inject._
import trading.core.lib.{ Consumer, Producer }
import trading.core.snapshots.SnapshotReader
import trading.events.TradeEvent

import cats.effect._
import cr.pulsar.{ Config, Pulsar, Subscription }
import dev.profunktor.redis4cats.effect.Log.Stdout._
import fs2.Stream

object Main extends IOApp.Simple {

  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { case (commands, engine) =>
        commands.through(engine.run)
      }
      .compile
      .drain

  val config = Config.Builder.default

  val cmdTopic    = AppTopic.TradingCommands.make(config)
  val eventsTopic = AppTopic.TradingEvents.make(config)

  val sub =
    Subscription.Builder
      .withName("trading-app")
      .withType(Subscription.Type.Shared)
      .build

  def resources =
    for {
      pulsar    <- Pulsar.make[IO](config.url)
      _         <- Resource.eval(IO.println(">>> Initializing trading service <<<"))
      producer  <- Producer.pulsar[IO, TradeEvent](pulsar, eventsTopic)
      snapshots <- SnapshotReader.make[IO]
      engine = Engine.make(producer, snapshots)
      commands <- Consumer.pulsar[IO, TradeCommand](pulsar, cmdTopic, sub).map(_.receive)
    } yield commands -> engine

}
