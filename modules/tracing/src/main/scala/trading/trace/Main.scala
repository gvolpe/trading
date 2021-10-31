package trading.trace

import trading.commands.TradeCommand
import trading.core.AppTopic
import trading.core.http.Ember
import trading.core.snapshots.SnapshotReader
import trading.events.TradeEvent
import trading.lib.*
import trading.state.{ DedupState, TradeState }

import cats.effect.*
import dev.profunktor.pulsar.schema.circe.bytes.*
import dev.profunktor.pulsar.{ Pulsar, Subscription }
import dev.profunktor.redis4cats.effect.Log.Stdout.*
import fs2.Stream
import natchez.EntryPoint
import natchez.honeycomb.Honeycomb

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { (server, events, commands, ep) =>
        Stream.eval(server.useForever).concurrently {
          events
            .either(commands)
            .evalMapAccumulate(
              List.empty[TradeEvent] -> List.empty[TradeCommand]
            )(Engine.fsm[IO](ep).run)
        }
      }
      .compile
      .drain

  def entryPoint(
      apiKey: Config.HoneycombApiKey
  ): Resource[IO, EntryPoint[IO]] =
    Honeycomb.entryPoint[IO]("trading-app") { ep =>
      IO {
        ep.setWriteKey(apiKey.value)
          .setDataset("trading-test3")
          .build
      }
    }

  val sub =
    Subscription.Builder
      .withName("tracing")
      .withType(Subscription.Type.Exclusive)
      .build

  // TODO: better do tracing with forecasts instead of trading?
  def resources =
    for
      config <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.make[IO](config.pulsar.url)
      _      <- Resource.eval(IO.println(">>> Initializing tracing service <<<"))
      evtTopic = AppTopic.TradingEvents.make(config.pulsar)
      cmdTopic = AppTopic.TradingCommands.make(config.pulsar)
      ep       <- entryPoint(config.honeycombApiKey)
      events   <- Consumer.pulsar[IO, TradeEvent](pulsar, evtTopic, sub).map(_.receive)
      commands <- Consumer.pulsar[IO, TradeCommand](pulsar, cmdTopic, sub).map(_.receive)
      server = Ember.default[IO](config.httpPort)
    yield (server, events, commands, ep)
