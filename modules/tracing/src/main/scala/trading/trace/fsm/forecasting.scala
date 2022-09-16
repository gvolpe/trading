package trading.trace
package fsm

import java.time.Instant

import scala.concurrent.duration.*

import trading.commands.*
import trading.domain.*
import trading.events.*
import trading.lib.{ FSM, Logger, Time }
import trading.trace.tracer.ForecastingTracer

import cats.{ Applicative, Monad }
import cats.syntax.all.*

type ForecastState = (List[AuthorEvent], List[ForecastEvent], List[ForecastCommand])
type ForecastIn    = AuthorEvent | ForecastEvent | ForecastCommand

object ForecastState:
  def empty: ForecastState =
    (List.empty, List.empty, List.empty)

def forecastFsm[F[_]: Applicative: Logger]: SM[F, ForecastIn] = tracer =>
  FSM {
    case ((atEvents, fcEvents, fcCommands), cmd: ForecastCommand) =>
      (atEvents.find(_.cid === cmd.cid), fcEvents.find(_.cid === cmd.cid)) match
        case (Some(evt), _) =>
          val ne = atEvents.filterNot(_.id === evt.id)
          val nc = fcCommands.filterNot(_.id === cmd.id)
          tracer.trace(cmd, evt.asLeft).tupleLeft((ne, fcEvents, nc))
        case (_, Some(evt)) =>
          val ne = fcEvents.filterNot(_.id === evt.id)
          val nc = fcCommands.filterNot(_.id === cmd.id)
          tracer.trace(cmd, evt.asRight).tupleLeft((atEvents, ne, nc))
        case (None, None) =>
          ().pure[F].tupleLeft((atEvents, fcEvents, fcCommands :+ cmd))

    case ((atEvents, fcEvents, fcCommands), evt: ForecastEvent) =>
      fcCommands.find(_.cid === evt.cid) match
        case Some(cmd) =>
          val ne = fcEvents.filterNot(_.id === evt.id)
          val nc = fcCommands.filterNot(_.id === cmd.id)
          tracer.trace(cmd, evt.asRight).tupleLeft((atEvents, ne, nc))
        case None =>
          ().pure[F].tupleLeft((atEvents, fcEvents :+ evt, fcCommands))

    case ((atEvents, fcEvents, fcCommands), evt: AuthorEvent) =>
      fcCommands.find(_.cid === evt.cid) match
        case Some(cmd) =>
          val ne = atEvents.filterNot(_.id === evt.id)
          val nc = fcCommands.filterNot(_.id === cmd.id)
          tracer.trace(cmd, evt.asLeft).tupleLeft((ne, fcEvents, nc))
        case None =>
          ().pure[F].tupleLeft((atEvents :+ evt, fcEvents, fcCommands))
  }
