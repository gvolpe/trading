package trading.feed

import java.time.Instant
import java.util.UUID

import scala.concurrent.duration.*

import trading.commands.ForecastCommand
import trading.core.AppTopic
import trading.domain.*
import trading.domain.generators.*
import trading.events.*
import trading.lib.{ *, given }

import cats.effect.*
import cats.effect.syntax.all.*
import cats.syntax.all.*
import dev.profunktor.pulsar.{ Producer as PulsarProducer, Pulsar, Subscription, Topic }
import fs2.Stream

object ForecastFeed:
  private val sub =
    Subscription.Builder
      .withName("forecasts-gen")
      .withType(Subscription.Type.Exclusive)
      .build

  private def settings[A: Shard](name: String) =
    PulsarProducer
      .Settings[IO, A]()
      .withDeduplication
      .withName(s"forecast-gen-$name-command")
      .withShardKey(Shard[A].key)
      .some

  // The randomness of randomUUID() seems better than that of Gen.uuid
  def makeCmdId = CommandId(UUID.randomUUID())

  // Simulates a flow of realistic commands and events with matching IDs:
  // 1. Send random Register command every 2 seconds.
  // 2. On every Registered event received, send a Publish command.
  // 3. On every Published event received, send a Vote command.
  def stream(
      fp: Producer[IO, ForecastCommand],
      fc: Consumer[IO, ForecastEvent],
      ac: Consumer[IO, AuthorEvent]
  ): Stream[IO, Unit] =
    val atEvents = ac.receive.evalMap { case AuthorEvent.Registered(_, cid, aid, _, _, _) =>
      publishCommandGen_(makeCmdId, cid, aid).sample.traverse_ { cmd =>
        IO.println(s">>> $cmd ") *> fp.send(cmd)
      }
    }

    val fcEvents = fc.receive.evalMap {
      case ForecastEvent.Published(_, cid, _, fid, _, _) =>
        voteCommandGen_(makeCmdId, cid, fid).sample.traverse_ { cmd =>
          IO.println(s">>> $cmd ") *> fp.send(cmd)
        }
      case _ => IO.unit
    }

    val uniqueCmds =
      Stream
        .repeatEval {
          registerCommandGen_(makeCmdId).sample.traverse_(fp.send)
        }
        .metered(2.seconds)
        .interruptAfter(6.seconds)

    Stream(
      uniqueCmds,
      atEvents,
      fcEvents
    ).parJoin(3)
      .interruptAfter(10.seconds)
