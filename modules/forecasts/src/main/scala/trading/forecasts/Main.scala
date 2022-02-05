package trading.forecasts

import trading.commands.ForecastCommand
import trading.core.AppTopic
import trading.core.http.Ember
import trading.events.*
import trading.forecasts.store.*
import trading.lib.{ given, * }

import cats.effect.*
import dev.profunktor.pulsar.{ Pulsar, Subscription }
import fs2.Stream

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { (server, consumer, engine) =>
        Stream.eval(server.useForever).concurrently {
          consumer.receiveM.evalMap(engine.run)
        }
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
      _      <- Resource.eval(Logger[IO].info("Initializing forecasts service"))
      cmdTopic      = AppTopic.ForecastCommands.make(config.pulsar)
      authorTopic   = AppTopic.AuthorEvents.make(config.pulsar)
      forecastTopic = AppTopic.ForecastEvents.make(config.pulsar)
      authors   <- Producer.pulsar[IO, AuthorEvent](pulsar, authorTopic)
      forecasts <- Producer.pulsar[IO, ForecastEvent](pulsar, forecastTopic)
      consumer  <- Consumer.pulsar[IO, ForecastCommand](pulsar, cmdTopic, sub)
      xa        <- DB.init[IO]
      atStore = AuthorStore.from(xa)
      fcStore = ForecastStore.from(xa)
      acker   = Acker.from(consumer)
      server  = Ember.default[IO](config.httpPort)
    yield (server, consumer, Engine.make(authors, forecasts, atStore, fcStore, acker))
