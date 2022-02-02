package trading.snapshots

import trading.core.TradeEngine
import trading.core.snapshots.SnapshotWriter
import trading.lib.{ Consumer, FSM, Logger }
import trading.events.TradeEvent
import trading.state.TradeState

import cats.MonadThrow
import cats.syntax.all.*

object Engine:
  def fsm[F[_]: MonadThrow: Logger](
      writer: SnapshotWriter[F],
      consumer: Consumer[F, TradeEvent]
  ): FSM[F, TradeState, Consumer.Msg[TradeEvent], Unit] =
    FSM {
      case (st, Consumer.Msg(msgId, TradeEvent.CommandExecuted(eid, _, cmd, _))) =>
        val nst = TradeEngine.fsm.runS(st, cmd)
        writer.save(nst).attempt.flatMap {
          case Left(e) =>
            Logger[F].warn(s"Failed to persist state for event ID: $eid") *>
              consumer.nack(msgId).tupleLeft(st)
          case Right(_) =>
            Logger[F].debug(s"State persisted for event ID: $eid") *>
              consumer.ack(msgId).attempt.void.tupleLeft(nst)
        }
      case (st, Consumer.Msg(msgId, evt)) =>
        Logger[F].debug(s"Event ID: ${evt.id}, no persistence") *>
          consumer.ack(msgId).attempt.void.tupleLeft(st)
    }
