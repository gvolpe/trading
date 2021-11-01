package trading.trace

import java.time.Instant

import scala.concurrent.duration.*

import trading.commands.*
import trading.events.*
import trading.lib.{ FSM, Logger }

import cats.Monad
import cats.syntax.all.*
import cats.Applicative

object Engine:
  type TradeIn = TradeEvent | TradeCommand

  def tradingFsm[F[_]: Logger: Monad](
      tracer: Tracer[F]
  ): FSM[F, (List[TradeEvent], List[TradeCommand]), TradeIn, Unit] =
    FSM {
      case ((events, commands), cmd: TradeCommand) =>
        Logger[F].info(s"Trading >>> Events: ${events.size}, Commands: ${commands.size}").flatMap { _ =>
          events.find(_.cid === cmd.cid) match
            case Some(evt) =>
              val ne = events.filterNot(_.id === evt.id)
              val nc = commands.filterNot(_.id === cmd.id)
              tracer.trading(evt, cmd).tupleLeft(ne -> nc)
            case None =>
              ().pure[F].tupleLeft(events -> (commands :+ cmd))
        }

      case ((events, commands), evt: TradeEvent) =>
        Logger[F].info(s"Trading >>> Events: ${events.size}, Commands: ${commands.size}").flatMap { _ =>
          commands.find(_.cid === evt.cid) match
            case Some(cmd) =>
              val ne = events.filterNot(_.id === evt.id)
              val nc = commands.filterNot(_.id === cmd.id)
              tracer.trading(evt, cmd).tupleLeft(ne -> nc)
            case None =>
              ().pure[F].tupleLeft((events :+ evt) -> commands)
        }
    }

  type ForecastState = (List[AuthorEvent], List[ForecastEvent], List[ForecastCommand])
  type ForecastIn    = AuthorEvent | ForecastEvent | ForecastCommand

  def forecastFsm[F[_]: Applicative: Logger](
      tracer: Tracer[F]
  ): FSM[F, ForecastState, ForecastIn, Unit] =
    FSM {
      case ((atEvents, fcEvents, fcCommands), cmd: ForecastCommand) =>
        (atEvents.find(_.cid === cmd.cid), fcEvents.find(_.cid === cmd.cid)) match
          case (Some(evt), _) =>
            val ne = atEvents.filterNot(_.id === evt.id)
            val nc = fcCommands.filterNot(_.id === cmd.id)
            tracer.forecasting(evt.asLeft, cmd).tupleLeft((ne, fcEvents, nc))
          case (_, Some(evt)) =>
            val ne = fcEvents.filterNot(_.id === evt.id)
            val nc = fcCommands.filterNot(_.id === cmd.id)
            tracer.forecasting(evt.asRight, cmd).tupleLeft((atEvents, ne, nc))
          case (None, None) =>
            ().pure[F].tupleLeft((atEvents, fcEvents, fcCommands :+ cmd))

      case ((atEvents, fcEvents, fcCommands), evt: ForecastEvent) =>
        fcCommands.find(_.cid === evt.cid) match
          case Some(cmd) =>
            val ne = fcEvents.filterNot(_.id === evt.id)
            val nc = fcCommands.filterNot(_.id === cmd.id)
            tracer.forecasting(evt.asRight, cmd).tupleLeft((atEvents, ne, nc))
          case None =>
            ().pure[F].tupleLeft((atEvents, fcEvents :+ evt, fcCommands))

      case ((atEvents, fcEvents, fcCommands), evt: AuthorEvent) =>
        fcCommands.find(_.cid === evt.cid) match
          case Some(cmd) =>
            val ne = atEvents.filterNot(_.id === evt.id)
            val nc = fcCommands.filterNot(_.id === cmd.id)
            tracer.forecasting(evt.asLeft, cmd).tupleLeft((ne, fcEvents, nc))
          case None =>
            ().pure[F].tupleLeft((atEvents :+ evt, fcEvents, fcCommands))
    }
