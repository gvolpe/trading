package trading.trace
package fsm

import java.time.Instant

import scala.concurrent.duration.*

import trading.commands.*
import trading.domain.*
import trading.events.*
import trading.lib.{ FSM, Logger, Time }

import cats.{ Applicative, Monad }
import cats.syntax.all.*
import natchez.Kernel

type MatchingIds = Map[CorrelationId, (Timestamp, Option[Kernel])]
type Kernels     = Map[CorrelationId, Kernel]

type TradeOne   = (List[TradeCommand], List[TradeEvent])
type TradeTwo   = (List[TradeEvent], List[Alert])
type TradeState = (TradeOne, TradeTwo, MatchingIds)
type TradeIn    = TradeCommand | TradeEvent | Alert

object TradeState:
  def empty: TradeState =
    ((List.empty -> List.empty), (List.empty, List.empty), Map.empty)

val MatchingIdsExpiration = 1.minute

def tradingFsm[F[_]: Logger: Monad: Time](
    tracer: Tracer[F]
): FSM[F, TradeState, TradeIn, Unit] =
  FSM {
    case (((commands, evts1), ea, ids), cmd: TradeCommand) =>
      tracePairs(tracer, cmd.cid)((commands :+ cmd, evts1), ea, ids).tupleRight(())

    case (((commands, evts1), (evts2, alerts), ids), evt: TradeEvent) =>
      tracePairs(tracer, evt.cid)((commands, evts1 :+ evt), (evts2 :+ evt, alerts), ids).tupleRight(())

    case ((ce, (evts2, alerts), ids), alt: Alert) =>
      tracePairs(tracer, alt.cid)(ce, (evts2, alerts :+ alt), ids).tupleRight(())
  }

private def tracePairs[F[_]: Monad: Time](
    tracer: Tracer[F],
    cid: CorrelationId
): TradeState => F[TradeState] =
  case ((_commands, _evts1), (_evts2, _alerts), _ids) =>
    Time[F].timestamp.flatMap { now =>
      val updatedIds =
        if _ids.get(cid).isEmpty then _ids.updated(cid, now -> None) else _ids

      val ids =
        updatedIds.toList.filter { case (_, (ts, _)) =>
          ts.value.plusSeconds(MatchingIdsExpiration.toSeconds).isAfter(now.value)
        }.toMap

      val commands = _commands.filter(c => ids.get(c.cid).nonEmpty)
      val evts1    = _evts1.filter(c => ids.get(c.cid).nonEmpty)
      val evts2    = _evts2.filter(c => ids.get(c.cid).nonEmpty)
      val alerts   = _alerts.filter(c => ids.get(c.cid).nonEmpty)

      for
        ((nc, ne1), kls) <- tracePairOne(tracer, commands, evts1)
        newIds = updateKernels(kls, ids)
        (ne2, na) <- tracePairTwo(tracer, evts2, alerts, newIds)
      yield ((nc, ne1), (ne2, na), newIds)
    }

private def updateKernels(
    kls: Kernels,
    cids: MatchingIds
): MatchingIds =
  cids.toList.map { case (cid, (ts, kl1)) =>
    cid -> (ts -> kl1.orElse(kls.get(cid)))
  }.toMap

private def tracePairOne[F[_]: Monad](
    tracer: Tracer[F],
    commands: List[TradeCommand],
    evts1: List[TradeEvent]
): F[(TradeOne, Kernels)] =
  val p1: List[F[(CorrelationId, Kernel)]] =
    for
      c <- commands
      e <- evts1
      if c.cid === e.cid
    yield tracer.trading1(c, e).map(e.cid -> _)

  p1.sequence.map { kls =>
    val nc = commands.filterNot(x => kls.map(_._1).contains(x.cid))
    val ne = evts1.filterNot(x => kls.map(_._1).contains(x.cid))
    (nc -> ne) -> kls.toMap
  }

private def tracePairTwo[F[_]: Monad](
    tracer: Tracer[F],
    evts2: List[TradeEvent],
    alerts: List[Alert],
    ids: MatchingIds
): F[TradeTwo] =
  val p2: List[F[Option[CorrelationId]]] =
    for
      e <- evts2
      a <- alerts
      if a.cid === e.cid
      k = ids.get(e.cid).flatMap(_._2)
    yield k.traverse(tracer.trading2(_, e, a).as(e.cid))

  p2.sequence.map(_.flatten).map { cids =>
    val na = alerts.filterNot(x => cids.contains(x.cid))
    val ne = evts2.filterNot(x => cids.contains(x.cid))
    ne -> na
  }
