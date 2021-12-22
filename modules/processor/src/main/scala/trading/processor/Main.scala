package trading.processor

import trading.commands.TradeCommand
import trading.core.AppTopic
import trading.core.http.Ember
import trading.core.snapshots.SnapshotReader
import trading.events.TradeEvent
import trading.lib.{given, *}
import trading.state.{ DedupState, TradeState }

import cats.effect.*
import dev.profunktor.pulsar.{ Pulsar, Subscription }
import fs2.Stream

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { (server, consumer, snapshots, fsm) =>
        Stream.eval(server.useForever).concurrently {
          Stream
            .eval(snapshots.latest.map(_.getOrElse(TradeState.empty)))
            .evalTap(latest => Logger[IO].debug(s"SNAPSHOTS: $latest"))
            .flatMap { latest =>
              consumer.receiveM.evalMapAccumulate(latest -> DedupState.empty)(fsm.run)
            }
        }
      }
      .compile
      .drain

  val sub =
    Subscription.Builder
      .withName("processor")
      .withType(Subscription.Type.KeyShared)
      .build

  def resources =
    for
      config <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.make[IO](config.pulsar.url)
      _      <- Resource.eval(Logger[IO].info("Initializing processor service"))
      server   = Ember.default[IO](config.httpPort)
      cmdTopic = AppTopic.TradingCommands.make(config.pulsar)
      evtTopic = AppTopic.TradingEvents.make(config.pulsar)
      producer  <- Producer.pulsar[IO, TradeEvent](pulsar, evtTopic)
      snapshots <- SnapshotReader.make[IO](config.redisUri)
      consumer  <- Consumer.pulsar[IO, TradeCommand](pulsar, cmdTopic, sub)
    yield (server, consumer, snapshots, Engine.fsm(producer, consumer.ack))
