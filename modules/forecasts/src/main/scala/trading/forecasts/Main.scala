package trading.forecasts

import trading.commands.ForecastCommand
import trading.core.AppTopic
import trading.core.http.Ember
import trading.events.*
import trading.forecasts.store.*
import trading.lib.{ Consumer, Producer }

import cats.effect.*
import dev.profunktor.pulsar.{ Pulsar, Subscription }
import dev.profunktor.pulsar.schema.circe.bytes.*
import dev.profunktor.redis4cats.effect.Log.Stdout.*
import fs2.Stream

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { (server, engine) =>
        Stream.eval(server.useForever).concurrently(engine.run)
      }
      .compile
      .drain

  val sub =
    Subscription.Builder
      .withName("forecasts")
      .withType(Subscription.Type.Failover)
      .build

  def resources =
    for
      config <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.make[IO](config.pulsar.url)
      _      <- Resource.eval(IO.println(">>> Initializing forecasts service <<<"))
      cmdTopic    = AppTopic.ForecastCommands.make(config.pulsar)
      eventsTopic = AppTopic.ForecastEvents.make(config.pulsar)
      authors   <- Producer.pulsar[IO, AuthorEvent](pulsar, eventsTopic)
      forecasts <- Producer.pulsar[IO, ForecastEvent](pulsar, eventsTopic)
      consumer  <- Consumer.pulsar[IO, ForecastCommand](pulsar, cmdTopic, sub)
      atStore <- AuthorStore.make[IO](config.redisUri) // TODO: Create RedisClient
      fcStore <- ForecastStore.make[IO](config.redisUri)
      server = Ember.default[IO](config.httpPort)
    yield server -> Engine.make(consumer, authors, forecasts, atStore, fcStore)
