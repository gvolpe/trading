package trading.processor

import trading.commands.TradeCommand
import trading.core.TradeEngine
import trading.domain.EventId
import trading.events.TradeEvent
import trading.lib.*
import trading.state.TradeState

import cats.MonadThrow
import cats.syntax.all.*

object Engine:
  def fsm[F[_]: GenUUID: Logger: MonadThrow: Time](
      producer: Producer[F, TradeEvent],
      ack: Consumer.MsgId => F[Unit]
  ): FSM[F, TradeState, Consumer.Msg[TradeCommand], Unit] =
    FSM { case (st, Consumer.Msg(msgId, _, cmd)) =>
      val (nst, event) = TradeEngine.fsm.run(st, cmd)
      for
        evt <- (GenUUID[F].make[EventId], Time[F].timestamp).mapN(event)
        ecs = TradeEvent._Command.get(evt).toList
        _ <- producer.send(evt)
        _ <- ack(msgId).attempt.void // don't care if this fails (de-dup)
      yield nst -> ()
    }
