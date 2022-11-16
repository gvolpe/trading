package trading.feed

import java.time.Instant
import java.util.UUID

import scala.concurrent.duration.*

import trading.commands.ForecastCommand
import trading.core.AppTopic
import trading.domain.*
import trading.domain.generators.*
import trading.events.*
import trading.lib.{ given, * }

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

  val ts = Timestamp(Instant.parse("2022-10-03T14:00:00.00Z"))

  def makeCmdId = CommandId(UUID.randomUUID())

  def cmd2(aid: AuthorId, cid: CorrelationId) =
    ForecastCommand.Publish(makeCmdId, cid, aid, Symbol.EURUSD, ForecastDescription("foo"), ForecastTag.Short, ts)

  def cmd3(fid: ForecastId, cid: CorrelationId) =
    ForecastCommand.Vote(makeCmdId, cid, fid, VoteResult.Up, ts)

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
      val cmd = cmd2(aid, cid)
      IO.println(s">>> $cmd ") *> fp.send(cmd)
    }

    val fcEvents = fc.receive.evalMap {
      case ForecastEvent.Published(_, cid, _, fid, _, _) =>
        val cmd = cmd3(fid, cid)
        IO.println(s">>> $cmd ") *> fp.send(cmd)
      case _ => IO.unit
    }

    val uniqueCmds =
      Stream
        .repeatEval {
          registerCommandGen.sample.traverse_ { cmd =>
            import ForecastCommand.*
            val unique = _CommandId.replace(makeCmdId)(cmd)
            fp.send(unique)
          }
        }
        .metered(2.seconds)
        .interruptAfter(6.seconds)

    Stream(
      uniqueCmds,
      atEvents,
      fcEvents
    ).parJoin(3)
      .interruptAfter(10.seconds)
