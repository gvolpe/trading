package trading.snapshots

import trading.core.TradeEngine
import trading.core.snapshots.SnapshotWriter
import trading.lib.{ Acker, FSM, Logger }
import trading.lib.Consumer.{ Msg, MsgId }
import trading.events.TradeEvent
import trading.state.TradeState

import cats.MonadThrow
import cats.syntax.all.*

type Tick = Unit

object Engine:
  type In = Msg[TradeEvent] | Tick

  def fsm[F[_]: MonadThrow: Logger](
      acker: Acker[F, TradeEvent],
      writer: SnapshotWriter[F]
  ): FSM[F, (TradeState, List[MsgId]), In, Unit] =
    FSM {
      case ((st, ids), Msg(msgId, _, TradeEvent.CommandExecuted(eid, _, cmd, _))) =>
        ().pure[F].tupleLeft(TradeEngine.fsm.runS(st, cmd) -> (ids :+ msgId))
      case (st, Msg(msgId, _, evt)) =>
        Logger[F].debug(s"Event ID: ${evt.id}, no persistence") *>
          acker.ack(msgId).attempt.void.tupleLeft(st)
      case ((st, ids), (_: Tick)) if ids.nonEmpty =>
        val lastId = ids.last
        writer
          .save(st, lastId)
          .attempt
          .flatMap {
            case Left(e) =>
              Logger[F].warn(s"Failed to persist state: $lastId").tupleLeft(st -> ids)
            case Right(_) =>
              Logger[F].debug(s"State persisted: $lastId. Acking ${ids.size} messages.") *>
                acker.ack(ids.toSet).attempt.map {
                  case Left(_)  => (st -> ids)        -> ()
                  case Right(_) => (st -> List.empty) -> ()
                }
          }
      case (st, (_: Tick)) =>
        ().pure[F].tupleLeft(st)
    }
