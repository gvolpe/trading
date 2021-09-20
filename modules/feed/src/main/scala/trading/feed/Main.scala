package trading.feed

import trading.commands.TradeCommand
import trading.core.AppTopic
import trading.lib.Producer
import trading.lib.inject._

import cats.effect._
import cr.pulsar.{ Config, Pulsar }

object Main extends IOApp.Simple {

  def run: IO[Unit] =
    resources.use(_.run)

  val config = Config.Builder.default

  val topic = AppTopic.TradingCommands.make(config)

  def resources =
    for {
      pulsar   <- Pulsar.make[IO](config.url)
      _        <- Resource.eval(IO.println(">>> Initializing feed service <<<"))
      producer <- Producer.pulsar[IO, TradeCommand](pulsar, topic)
    } yield Feed.random(producer)

}
