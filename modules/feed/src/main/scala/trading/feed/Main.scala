package trading.feed

import java.nio.charset.StandardCharsets.UTF_8

import trading.commands.TradeCommand
import trading.core.AppTopic
import trading.core.http.Ember
import trading.lib.Producer

import cats.effect.*
import cats.syntax.all.*
import dev.profunktor.pulsar.{ Pulsar, ShardKey }
import dev.profunktor.pulsar.schema.circe.bytes.*
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

  val cmdShardKey: TradeCommand => ShardKey =
    cmd => ShardKey.Of(cmd.symbol.value.getBytes(UTF_8))

  def resources =
    for
      config <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.make[IO](config.pulsar.url)
      _      <- Resource.eval(IO.println(">>> Initializing feed service <<<"))
      topic = AppTopic.TradingCommands.make(config.pulsar)
      producer <- Producer.pulsar[IO, TradeCommand](pulsar, topic, cmdShardKey)
      server = Ember.default[IO]
    yield server -> Feed.random(producer)
