package trading.processor

import trading.commands.TradeCommand
import trading.core.{ Conflicts, EventSource }
import trading.domain.EventId
import trading.events.TradeEvent
import trading.lib.*
import trading.state.{ DedupState, TradeState }

import cats.MonadThrow
import cats.syntax.all.*
import fs2.Stream

object Engine:
  def fsm[F[_]: GenUUID: Logger: MonadThrow: Time](
      producer: Producer[F, TradeEvent],
      ack: Consumer.MsgId => F[Unit]
  ): FSM[F, (TradeState, DedupState), Consumer.Msg[TradeCommand], Unit] =
    FSM { case ((st, ds), Consumer.Msg(msgId, command)) =>
      Conflicts.dedup(ds)(command) match
        case None =>
          Logger[F].warn(s"Deduplicated Command ID: ${command.id.show}").tupleLeft(st -> ds)
        case Some(cmd) =>
          val (nst, events) = EventSource.run(st)(cmd)
          for
            evs <- events.traverse((GenUUID[F].make[EventId], Time[F].timestamp).mapN(_))
            _   <- evs.traverse(producer.send)
            nds <- Time[F].timestamp.map(Conflicts.updateMany(ds)(evs.map(_.command), _))
            _ <- ack(msgId).attempt.void // don't care if this fails (de-dup)
          yield (nst -> nds) -> ()
    }
