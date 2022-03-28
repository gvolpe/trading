package trading.feed

import trading.commands.*
import trading.core.AppTopic
import trading.core.http.Ember
import trading.lib.{ Logger, Producer }

import cats.effect.*
import cats.syntax.all.*
import dev.profunktor.pulsar.Pulsar
import fs2.Stream

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    resources.use { (server, feed) =>
      Stream
        .eval(server.useForever)
        .concurrently(Stream.eval(feed))
        .compile
        .drain
    }

  def resources =
    for
      config <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.make[IO](config.pulsar.url)
      _      <- Resource.eval(Logger[IO].info("Initializing feed service"))
      trTopic = AppTopic.TradingCommands.make(config.pulsar)
      fcTopic = AppTopic.ForecastCommands.make(config.pulsar)
      trading     <- Producer.sharded[IO, TradeCommand](pulsar, trTopic)
      forecasting <- Producer.dedup[IO, ForecastCommand](pulsar, fcTopic)
      server = Ember.default[IO](config.httpPort)
    yield server -> Feed.random(trading, forecasting)
