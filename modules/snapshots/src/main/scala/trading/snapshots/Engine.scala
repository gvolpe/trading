package trading.snapshots

import trading.core.TradeEngine
import trading.core.snapshots.SnapshotWriter
import trading.lib.{ Acker, FSM, Logger }
import trading.lib.Consumer.Msg
import trading.events.TradeEvent
import trading.state.TradeState

import cats.MonadThrow
import cats.syntax.all.*

object Engine:
  def fsm[F[_]: MonadThrow: Logger](
      acker: Acker[F, TradeEvent],
      writer: SnapshotWriter[F]
  ): FSM[F, TradeState, Msg[TradeEvent], Unit] =
    FSM {
      case (st, Msg(msgId, _, TradeEvent.CommandExecuted(eid, _, cmd, _))) =>
        val nst = TradeEngine.fsm.runS(st, cmd)
        writer.save(nst).attempt.flatMap {
          case Left(e) =>
            Logger[F].warn(s"Failed to persist state for event ID: $eid") *>
              acker.nack(msgId).tupleLeft(st)
          case Right(_) =>
            Logger[F].debug(s"State persisted for event ID: $eid") *>
              acker.ack(msgId).attempt.void.tupleLeft(nst)
        }
      case (st, Msg(msgId, _, evt)) =>
        Logger[F].debug(s"Event ID: ${evt.id}, no persistence") *>
          acker.ack(msgId).attempt.void.tupleLeft(st)
    }
