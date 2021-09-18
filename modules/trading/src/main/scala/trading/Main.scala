package trading

import trading.commands.TradeCommand
import trading.core.inject._
import trading.core.lib.Producer
import trading.core.snapshots.SnapshotReader
import trading.core.{AppTopic, Time}
import trading.domain.TradeAction
import trading.events.TradeEvent

import cats.effect._
import cr.pulsar.{ Config, Pulsar }
import dev.profunktor.redis4cats.effect.Log.Stdout._
import fs2.Stream

object Main extends IOApp.Simple {

  val commands: Stream[IO, TradeCommand] =
    Stream.eval(Time[IO].timestamp).map { ts =>
      TradeCommand.Create("EURPLN", TradeAction.Ask, 4.57484, 10, "test", ts)
    }

  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { case (producer, snapshots) =>
        val engine = Engine.make(producer, snapshots)
        commands.through(engine.run)
      }
      .compile
      .drain

  val config = Config.Builder.default

  val topic = AppTopic.TradingEvents.make(config)

  def resources =
    for {
      pulsar    <- Pulsar.make[IO](config.url)
      _         <- Resource.eval(IO.println(">>> Initializing trading service <<<"))
      producer  <- Producer.pulsar[IO, TradeEvent](pulsar, topic)
      snapshots <- SnapshotReader.make[IO]
      //commands <- Consumer.pulsar[IO, TradeCommand](pulsar, cmdTopic, sub).map(_.receive)
    } yield producer -> snapshots

}
