package trading.snapshots

import trading.core.TradeEngine
import trading.core.snapshots.SnapshotWriter
import trading.lib.{ Acker, FSM, Logger }
import trading.lib.Consumer.{ Msg, MsgId }
import trading.events.*
import trading.events.TradeEvent.*
import trading.state.TradeState

import cats.MonadThrow
import cats.syntax.all.*

type Tick = Unit

object Engine:
  type In = Either[Msg[TradeEvent], Msg[SwitchEvent]] | Tick

  def fsm[F[_]: MonadThrow: Logger](
      tradeAcker: Acker[F, TradeEvent],
      switchAcker: Acker[F, SwitchEvent],
      writer: SnapshotWriter[F]
  ): FSM[F, (TradeState, List[MsgId]), In, Unit] =
    FSM {
      case ((st, ids), Left(Msg(msgId, _, evt @ CommandExecuted(_, _, _, _)))) =>
        ().pure[F].tupleLeft(TradeEngine.eventsFsm.runS(st, evt) -> (ids :+ msgId))
      case (st, Left(Msg(msgId, _, CommandRejected(evId, _, _, _, _)))) =>
        Logger[F].debug(s"Event ID: $evId, no persistence") *>
          tradeAcker.ack(msgId).tupleLeft(st)
      case ((st, ids), Right(Msg(msgId, _, evt))) =>
        val nst = TradeEngine.eventsFsm.runS(st, evt)
        writer.saveStatus(nst.status).whenA(nst.status =!= st.status) *>
          switchAcker.ack(msgId).tupleLeft(nst, ids)
      case ((st, ids), (_: Tick)) if ids.nonEmpty =>
        val lastId = ids.last
        writer.save(st, lastId).attempt.flatMap {
          case Left(e) =>
            Logger[F].warn(s"Failed to persist state: $lastId").tupleLeft(st -> ids)
          case Right(_) =>
            Logger[F].debug(s"State persisted: $lastId. Acking ${ids.size} messages.") *>
              tradeAcker.ack(ids.toSet).attempt.flatMap {
                case Left(e)  => Logger[F].error(e.getMessage).tupleLeft(st -> ids)
                case Right(_) => ((st -> List.empty) -> ()).pure[F]
              }
        }
      case (st, (_: Tick)) =>
        ().pure[F].tupleLeft(st)
    }
