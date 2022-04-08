package trading.processor

import trading.commands.*
import trading.core.TradeEngine
import trading.domain.{ EventId, Timestamp }
import trading.domain.TradingStatus.{ Off, On }
import trading.events.*
import trading.lib.*
import trading.state.TradeState

import cats.effect.kernel.{ MonadCancelThrow, Resource }
import cats.syntax.all.*

object Engine:
  type In = Either[Consumer.Msg[TradeCommand], Consumer.Msg[SwitchCommand]]

  def fsm[F[_]: GenUUID: Logger: MonadCancelThrow: Time](
      producer: Producer[F, TradeEvent],
      switcher: Producer[F, SwitchEvent],
      pulsarTx: Resource[F, Txn],
      tradeAcker: Acker[F, TradeCommand],
      switchAcker: Acker[F, SwitchCommand]
  ): FSM[F, TradeState, In, Unit] =
    def sendEvent(
        f: (EventId, Timestamp) => TradeEvent | SwitchEvent,
        msgId: Consumer.MsgId,
        ack: F[Unit]
    ): F[Unit] =
      (GenUUID[F].make[EventId], Time[F].timestamp).mapN(f).flatMap {
        case e: TradeEvent  => producer.send(e)
        case e: SwitchEvent => switcher.send(e)
      } *> ack

    FSM {
      case (st, Right(Consumer.Msg(msgId, _, cmd))) =>
        val (nst, evt) = TradeEngine.fsm.run(st, cmd)
        pulsarTx
          .use { tx =>
            sendEvent(evt, msgId, switchAcker.ack(msgId, tx)).tupleLeft(nst)
          }
          .handleErrorWith { e =>
            Logger[F].warn(s"Transaction failed: ${e.getMessage}").tupleLeft(st)
          }
      case (st, Left(Consumer.Msg(msgId, _, cmd))) =>
        val (nst, evt) = TradeEngine.fsm.run(st, cmd)
        sendEvent(evt, msgId, tradeAcker.ack(msgId)).tupleLeft(nst)
    }
