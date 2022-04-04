package trading.processor

import trading.commands.TradeCommand
import trading.core.TradeEngine
import trading.domain.EventId
import trading.domain.TradingStatus.{ Off, On }
import trading.events.TradeEvent
import trading.events.TradeEvent.Switch
import trading.lib.*
import trading.state.TradeState

import cats.MonadThrow
import cats.syntax.all.*

object Engine:
  type In = Consumer.Msg[TradeCommand] | Switch

  def fsm[F[_]: GenUUID: Logger: MonadThrow: Time](
      producer: Producer[F, TradeEvent],
      switcher: Producer[F, TradeEvent.Switch],
      ack: Consumer.MsgId => F[Unit]
  ): FSM[F, TradeState, In, Unit] =
    FSM {
      case (st, Consumer.Msg(msgId, _, cmd)) =>
        val (nst, evt) = TradeEngine.fsm.run(st, cmd)
        for
          e <- (GenUUID[F].make[EventId], Time[F].timestamp).mapN(evt)
          _ <- producer.send(e)
          _ <- Switch.from(e).traverse_(switcher.send)
          _ <- ack(msgId).attempt.void // don't care if this fails (de-dup)
        yield nst -> ()
      case (st, Switch(Left(_: TradeEvent.Started))) =>
        ().pure[F].tupleLeft(TradeState._Status.replace(On)(st))
      case (st, Switch(Right(_: TradeEvent.Stopped))) =>
        ().pure[F].tupleLeft(TradeState._Status.replace(Off)(st))
    }
