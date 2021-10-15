package trading.feed

import java.nio.charset.StandardCharsets.UTF_8

import trading.commands.TradeCommand
import trading.core.AppTopic
import trading.core.http.Ember
import trading.lib.Producer
import trading.lib.inject.given

import cats.effect.*
import cats.syntax.all.*
import dev.profunktor.pulsar.{ Config, Pulsar, ShardKey }
import fs2.Stream

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    resources.use { (server, feed) =>
      Stream
        .eval(server.useForever)
        .concurrently(Stream.eval(feed.run))
        .compile
        .drain
    }

  val config = Config.Builder.default

  val topic = AppTopic.TradingCommands.make(config)

  val cmdShardKey: TradeCommand => ShardKey =
    cmd => ShardKey.Of(cmd.symbol.value.getBytes(UTF_8))

  def resources =
    for {
      pulsar   <- Pulsar.make[IO](config.url)
      _        <- Resource.eval(IO.println(">>> Initializing feed service <<<"))
      producer <- Producer.pulsar[IO, TradeCommand](pulsar, topic, cmdShardKey)
      server = Ember.default[IO]
    } yield server -> Feed.random(producer)
