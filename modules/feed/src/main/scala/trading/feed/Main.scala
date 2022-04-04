package trading.feed

import trading.commands.*
import trading.core.AppTopic
import trading.core.http.Ember
import trading.lib.*

import cats.effect.*
import cats.syntax.all.*
import dev.profunktor.pulsar.{ Producer as PulsarProducer, Pulsar, SeqIdMaker }
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

  val tcSettings =
    PulsarProducer
      .Settings[IO, TradeCommand]()
      .withDeduplication(SeqIdMaker.fromEq)
      .withShardKey(Shard[TradeCommand].key)
      .some

  val fcSettings =
    PulsarProducer
      .Settings[IO, ForecastCommand]()
      .withDeduplication(SeqIdMaker.fromEq)
      .withShardKey(Shard[ForecastCommand].key)
      .some

  def resources =
    for
      config <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.make[IO](config.pulsar.url)
      _      <- Resource.eval(Logger[IO].info("Initializing feed service"))
      trTopic = AppTopic.TradingCommands.make(config.pulsar)
      fcTopic = AppTopic.ForecastCommands.make(config.pulsar)
      trading     <- Producer.pulsar[IO, TradeCommand](pulsar, trTopic, tcSettings)
      forecasting <- Producer.pulsar[IO, ForecastCommand](pulsar, fcTopic, fcSettings)
      server = Ember.default[IO](config.httpPort)
    yield server -> Feed.random(trading, forecasting)
