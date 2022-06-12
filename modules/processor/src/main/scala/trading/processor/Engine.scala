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
        ack: Txn => F[Unit],
        st: TradeState,
        cmd: TradeCommand | SwitchCommand
    ): F[(TradeState, Unit)] =
      pulsarTx
        .use { tx =>
          val (nst, evt) = TradeEngine.fsm.run(st, cmd)
          (GenUUID[F].make[EventId], Time[F].timestamp).mapN(evt).flatMap {
            case e: TradeEvent  => producer.send(e, tx)
            case e: SwitchEvent => switcher.send(e, tx)
          } *> ack(tx).tupleLeft(nst)
        }
        .handleErrorWith { e =>
          Logger[F].warn(s"Transaction failed: ${e.getMessage}").tupleLeft(st)
        }

    FSM {
      case (st, Right(Consumer.Msg(msgId, _, cmd))) =>
        sendEvent(switchAcker.ack(msgId, _), st, cmd)
      case (st, Left(Consumer.Msg(msgId, _, cmd))) =>
        sendEvent(tradeAcker.ack(msgId, _), st, cmd)
    }
