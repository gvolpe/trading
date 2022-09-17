package trading.trace
package fsm

import java.time.Instant

import scala.concurrent.duration.*

import trading.commands.*
import trading.domain.*
import trading.events.*
import trading.lib.{ FSM, Logger, Time }
import trading.trace.tracer.TradingTracer

import cats.{ Applicative, Monad }
import cats.syntax.all.*
import natchez.Kernel

type MatchingIds = Map[CorrelationId, (Timestamp, Option[CmdKernel], Option[EvtKernel])]
type Tick        = Unit
type TradeState  = (List[TradeEvent], List[Alert], MatchingIds)
type TradeIn     = TradeCommand | TradeEvent | Alert | Tick

val MatchingIdsExpiration = 1.minute

object TradeState:
  def empty: TradeState = (List.empty, List.empty, Map.empty)

def tradingFsm[F[_]: Logger: Monad: Time]: SM[F, TradeIn] = tracer =>
  FSM {
    case ((events, alerts, ids), cmd: TradeCommand) =>
      for
        k <- tracer.command(cmd)
        i <- updateMatchingIds(ids, cmd.cid, Left(k))
      yield (events, alerts, i) -> ()

    case (st @ (events, alerts, ids), evt: TradeEvent) =>
      ids.get(evt.cid).flatMap((_, k, _) => k) match
        case Some(cmdKernel) =>
          for
            k <- tracer.event(cmdKernel, evt)
            i <- updateMatchingIds(ids, evt.cid, Right(k))
          yield (events, alerts, i) -> ()
        case None =>
          expireMatchingIds[F](ids).map(i => (events :+ evt, alerts, i) -> ())

    case (st @ (events, alerts, ids), alt: Alert) =>
      ids.get(alt.cid).flatMap((_, _, k) => k) match
        case Some(evtKernel) =>
          tracer.alert(evtKernel, alt).as(st -> ())
        case None =>
          expireMatchingIds[F](ids).map(i => (events, alerts :+ alt, i) -> ())

    case (st @ (events, alerts, ids), tick: Tick) =>
      val fsm = tradingFsm[F].apply(tracer)

      val processEvents: F[TradeState] =
        events.foldLeft(st.pure[F]) { (getSt, evt) =>
          getSt.flatMap(fsm.runS(_, evt))
        }

      def processAlerts(st1: TradeState): F[TradeState] =
        alerts.foldLeft(st1.pure[F]) { (getSt, alt) =>
          getSt.flatMap(fsm.runS(_, alt))
        }

      (processEvents >>= processAlerts).tupleRight(())
  }

def updateMatchingIds[F[_]: Monad: Time](
    ids: MatchingIds,
    cid: CorrelationId,
    kernel: Either[CmdKernel, EvtKernel]
): F[MatchingIds] =
  Time[F].timestamp.flatMap { now =>
    def update(ts: Timestamp, k1: Option[CmdKernel], k2: Option[EvtKernel]) =
      kernel match
        case Left(nk1)  => (ts, nk1.some, k2).some
        case Right(nk2) => (ts, k1, nk2.some).some

    expireMatchingIds {
      ids.updatedWith(cid) {
        case Some(ts, k1, k2) => update(ts, k1, k2)
        case None             => update(now, None, None)
      }
    }
  }

def expireMatchingIds[F[_]: Monad: Time](
    ids: MatchingIds
): F[MatchingIds] =
  Time[F].timestamp.map { now =>
    ids.filter { case (_, (ts, _, _)) =>
      ts.value.plusSeconds(MatchingIdsExpiration.toSeconds).isAfter(now.value)
    }
  }
