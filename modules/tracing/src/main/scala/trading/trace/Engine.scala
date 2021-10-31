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
  def tradingFsm[F[_]: Logger: Monad](
      tracer: Tracer[F]
  ): FSM[F, (List[TradeEvent], List[TradeCommand]), Either[TradeEvent, TradeCommand], Unit] =
    FSM {
      case ((events, commands), Right(cmd)) =>
        Logger[F].info(s"Trading >>> Events: ${events.size}, Commands: ${commands.size}").flatMap { _ =>
          events.find(_.cid === cmd.cid) match
            case Some(evt) =>
              val ne = events.filterNot(_.id === evt.id)
              val nc = commands.filterNot(_.id === cmd.id)
              tracer.trading(evt, cmd).tupleLeft(ne -> nc)
            case None =>
              ().pure[F].tupleLeft(events -> (commands :+ cmd))
        }

      case ((events, commands), Left(evt)) =>
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
  type ForecastIn    = Either[AuthorEvent, Either[ForecastEvent, ForecastCommand]]

  def forecastFsm[F[_]: Applicative: Logger](
      tracer: Tracer[F]
  ): FSM[F, ForecastState, ForecastIn, Unit] =
    FSM {
      case ((atEvents, fcEvents, fcCommands), Right(Right(cmd))) =>
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
            ().pure[F].tupleLeft((atEvents, fcEvents, (fcCommands :+ cmd)))

      case ((atEvents, fcEvents, fcCommands), Right(Left(fcEvt))) =>
        ().pure[F].tupleLeft((atEvents, fcEvents, fcCommands))

      case ((atEvents, fcEvents, fcCommands), Left(atEvt)) =>
        ().pure[F].tupleLeft((atEvents, fcEvents, fcCommands))
    }
