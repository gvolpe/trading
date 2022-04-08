package trading.snapshots

import trading.core.TradeEngine
import trading.core.snapshots.SnapshotWriter
import trading.lib.{ Acker, FSM, Logger }
import trading.lib.Consumer.{ Msg, MsgId }
import trading.events.*
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
      case ((st, ids), Left(Msg(msgId, _, TradeEvent.CommandExecuted(_, _, cmd, _)))) =>
        ().pure[F].tupleLeft(TradeEngine.fsm.runS(st, cmd) -> (ids :+ msgId))
      case (st, Left(Msg(msgId, _, evt))) =>
        Logger[F].debug(s"Event ID: ${evt.id}, no persistence") *>
          tradeAcker.ack(msgId).attempt.void.tupleLeft(st)
      case ((st, ids), Right(Msg(msgId, _, evt))) =>
        val nst = evt.getCommand.map(TradeEngine.fsm.runS(st, _)).getOrElse(st)
        switchAcker.ack(msgId).attempt.void.tupleLeft(nst, ids)
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
                tradeAcker.ack(ids.toSet).attempt.map {
                  case Left(_)  => (st -> ids)        -> ()
                  case Right(_) => (st -> List.empty) -> ()
                }
          }
      case (st, (_: Tick)) =>
        ().pure[F].tupleLeft(st)
    }
