package trading.trace

import scala.concurrent.duration.*

import trading.commands.TradeCommand
import trading.events.TradeEvent
import trading.lib.{ FSM, Logger }

import cats.Monad
import cats.effect.{ Trace as _, * }
import cats.syntax.all.*
import io.circe.syntax.*
import natchez.*
import natchez.honeycomb.Honeycomb
import java.time.Instant

object Engine:
  //TODO: DoTrace could be some external component, so we can easily mock it for testing with a ref
  private def doTrace[F[_]: Async](
      ep: EntryPoint[F],
      evt: TradeEvent,
      cmd: TradeCommand
  ): F[Unit] =
    ep.root("trading-root").use { root =>
      root.span("trading-tx").use { sp =>
        val durationMs = evt.createdAt.value.toEpochMilli - cmd.createdAt.value.toEpochMilli
        sp.put("correlation-id"      -> evt.cid.show) *>
          sp.put("command-timestamp" -> cmd.createdAt.show) *>
          sp.put("event-timestamp" -> evt.createdAt.show) *>
          sp.put("duration-tx-ms" -> durationMs.show) *>
          sp.put("payload-event" -> evt.asJson.noSpaces) *>
          sp.put("payload-command" -> cmd.asJson.noSpaces)
      }
    }

  def fsm[F[_]: Async: Logger](
      ep: EntryPoint[F]
  ): FSM[F, (List[TradeEvent], List[TradeCommand]), Either[TradeEvent, TradeCommand], Unit] =
    FSM {
      case ((events, commands), Right(cmd)) =>
        Logger[F].info(s"Events: ${events.size}, Commands: ${commands.size}").flatMap { _ =>
          events.find(_.cid === cmd.cid) match
            case Some(evt) =>
              val ne = events.filterNot(_.id === evt.id)
              val nc = commands.filterNot(_.id === cmd.id)
              doTrace(ep, evt, cmd).tupleLeft(ne -> nc)
            case None =>
              ().pure[F].tupleLeft(events -> (commands :+ cmd))
        }

      case ((events, commands), Left(evt)) =>
        Logger[F].info(s"Events: ${events.size}, Commands: ${commands.size}").flatMap { _ =>
          commands.find(_.cid === evt.cid) match
            case Some(cmd) =>
              val ne = events.filterNot(_.id === evt.id)
              val nc = commands.filterNot(_.id === cmd.id)
              doTrace(ep, evt, cmd).tupleLeft(ne -> nc)
            case None =>
              ().pure[F].tupleLeft((events :+ evt) -> commands)
        }
    }
