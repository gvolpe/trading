package trading.processor

import trading.commands.TradeCommand
import trading.core.AppTopic
import trading.core.dedup.DedupRegistry
import trading.core.http.Ember
import trading.core.snapshots.SnapshotReader
import trading.events.TradeEvent
import trading.lib.{ given, * }
import trading.state.{ DedupState, TradeState }

import cats.effect.*
import cats.syntax.apply.*
import dev.profunktor.pulsar.{ Pulsar, Subscription }
import fs2.Stream

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { (server, consumer, snapshots, registry, fsm) =>
        Stream.eval(server.useForever).concurrently {
          val s = snapshots.latest.map(_.getOrElse(TradeState.empty))
          val d = registry.get

          Stream
            .eval((s, d).tupled)
            .flatMap { (sn, dp) =>
              val log = Logger[IO].debug(s"SNAPSHOTS: $sn") *> Logger[IO].debug(s"DEDUP: $dp")
              Stream.eval(log) ++ consumer.receiveM.evalMapAccumulate(sn -> dp)(fsm.run)
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
      registry  <- DedupRegistry.make[IO](config.redisUri, config.appId, config.keyExpiration)
      snapshots <- SnapshotReader.make[IO](config.redisUri)
      consumer  <- Consumer.pulsar[IO, TradeCommand](pulsar, cmdTopic, sub)
    yield (server, consumer, snapshots, registry, Engine.fsm(producer, registry, consumer.ack))
