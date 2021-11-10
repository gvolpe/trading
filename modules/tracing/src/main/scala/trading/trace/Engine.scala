package trading.trace

import java.time.Instant

import scala.concurrent.duration.*

import trading.commands.*
import trading.domain.Alert
import trading.events.*
import trading.lib.{ FSM, Logger }

import cats.Monad
import cats.syntax.all.*
import cats.Applicative

object Engine:
  type TradeState = (List[TradeCommand], List[TradeEvent], List[Alert])
  type TradeIn    = TradeCommand | TradeEvent | Alert

  def tradingFsm[F[_]: Logger: Monad](
      tracer: Tracer[F]
  ): FSM[F, TradeState, TradeIn, Unit] =
    FSM {
      case ((commands, events, alerts), cmd: TradeCommand) =>
        (events.find(_.cid === cmd.cid), alerts.find(_.cid === cmd.cid)) match
          case (Some(evt), Some(alt)) =>
            val nc = commands.filterNot(_.id === cmd.id)
            val ne = events.filterNot(_.id === evt.id)
            val na = alerts.filterNot(_.id === alt.id)
            tracer.trading(cmd, evt, alt).tupleLeft((nc, ne, na))
          case (Some(evt), None) =>
            ().pure[F].tupleLeft((commands, events :+ evt, alerts))
          case (None, Some(alt)) =>
            ().pure[F].tupleLeft((commands, events, alerts :+ alt))
          case (None, None) =>
            ().pure[F].tupleLeft((commands :+ cmd, events, alerts))

      case ((commands, events, alerts), evt: TradeEvent) =>
        (commands.find(_.cid === evt.cid), alerts.find(_.cid === evt.cid)) match
          case (Some(cmd), Some(alt)) =>
            val nc = commands.filterNot(_.id === cmd.id)
            val ne = events.filterNot(_.id === evt.id)
            val na = alerts.filterNot(_.id === alt.id)
            tracer.trading(cmd, evt, alt).tupleLeft((nc, ne, na))
          case _ =>
            ().pure[F].tupleLeft((commands, events :+ evt, alerts))

      case ((commands, events, alerts), alt: Alert) =>
        (commands.find(_.cid === alt.cid), events.find(_.cid === alt.cid)) match
          case (Some(cmd), Some(evt)) =>
            val nc = commands.filterNot(_.id === cmd.id)
            val ne = events.filterNot(_.id === evt.id)
            val na = alerts.filterNot(_.id === alt.id)
            tracer.trading(cmd, evt, alt).tupleLeft((nc, ne, na))
          case _ =>
            ().pure[F].tupleLeft((commands, events, alerts :+ alt))
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
