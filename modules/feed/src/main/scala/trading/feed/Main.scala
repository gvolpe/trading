package trading.feed

import trading.commands.TradeCommand
import trading.core.AppTopic
import trading.core.http.Ember
import trading.lib.Producer

import cats.effect.*
import cats.syntax.all.*
import dev.profunktor.pulsar.schema.circe.bytes.*
import dev.profunktor.pulsar.{ Pulsar, ShardKey }
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

  def resources =
    for
      config <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.make[IO](config.pulsar.url)
      _      <- Resource.eval(IO.println(">>> Initializing feed service <<<"))
      topic = AppTopic.TradingCommands.make(config.pulsar)
      producer <- Producer.sharded[IO, TradeCommand](pulsar, topic)
      server = Ember.default[IO](config.httpPort)
    yield server -> Feed.random(producer)
