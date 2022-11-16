package trading.feed

import trading.commands.*
import trading.core.AppTopic
import trading.core.http.Ember
import trading.domain.CommandId
import trading.events.*
import trading.lib.*

import cats.effect.*
import cats.syntax.all.*
import dev.profunktor.pulsar.{ Producer as PulsarProducer, Pulsar, Subscription }
import fs2.Stream

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    resources.use { (server, feed, fcFeed) =>
      Stream
        .eval(server.useForever)
        .concurrently {
          Stream(
            Stream.eval(feed),
            fcFeed
          ).parJoin(2)
        }
        .compile
        .drain
    }

  // TradeCommand / ForecastCommand producer settings, dedup (retries) and sharded
  def settings[A: Shard](name: String) =
    PulsarProducer
      .Settings[IO, A]()
      .withDeduplication
      .withName(s"feed-$name-command")
      .withShardKey(Shard[A].key)
      .some

  // SwitchEvent producer settings, dedup (retries) and partitioned (for topic compaction)
  val swSettings =
    PulsarProducer
      .Settings[IO, SwitchCommand]()
      .withDeduplication
      .withMessageKey(Compaction[SwitchCommand].key)
      .withName("feed-switch-command")
      .some

  val sub =
    Subscription.Builder
      .withName("forecasts-gen")
      .withType(Subscription.Type.Exclusive)
      .build

  def resources =
    for
      config <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.make[IO](config.pulsar.url)
      _      <- Resource.eval(Logger[IO].info("Initializing feed service"))
      trTopic   = AppTopic.TradingCommands.make(config.pulsar)
      swTopic   = AppTopic.SwitchCommands.make(config.pulsar)
      fcTopic   = AppTopic.ForecastCommands.make(config.pulsar)
      atEvTopic = AppTopic.AuthorEvents.make(config.pulsar)
      fcEvTopic = AppTopic.ForecastEvents.make(config.pulsar)
      trading     <- Producer.pulsar[IO, TradeCommand](pulsar, trTopic, settings("trade"))
      switcher    <- Producer.pulsar[IO, SwitchCommand](pulsar, swTopic, swSettings)
      forecasting <- Producer.pulsar[IO, ForecastCommand](pulsar, fcTopic, settings("forecast"))
      fcConsumer  <- Consumer.pulsar[IO, ForecastEvent](pulsar, fcEvTopic, sub)
      atConsumer  <- Consumer.pulsar[IO, AuthorEvent](pulsar, atEvTopic, sub)
      fcFeed = ForecastFeed.stream(forecasting, fcConsumer, atConsumer)
      server = Ember.default[IO](config.httpPort)
    yield (server, Feed.random(trading, switcher, forecasting), fcFeed)
