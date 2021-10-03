package trading.feed

import java.nio.charset.StandardCharsets.UTF_8

import trading.commands.TradeCommand
import trading.core.AppTopic
import trading.lib.Producer
import trading.lib.inject.circeBytesInject

import cats.effect.*
import dev.profunktor.pulsar.{ Config, Pulsar, ShardKey }

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    resources.use(_.run)

  val config = Config.Builder.default

  val topic = AppTopic.TradingCommands.make(config)

  val cmdShardKey: TradeCommand => ShardKey =
    cmd => ShardKey.Of(cmd.symbol.getBytes(UTF_8))

  def resources =
    for {
      pulsar   <- Pulsar.make[IO](config.url)
      _        <- Resource.eval(IO.println(">>> Initializing feed service <<<"))
      producer <- Producer.pulsar[IO, TradeCommand](pulsar, topic, cmdShardKey)
    } yield Feed.random(producer)
