package trading.processor

import trading.commands.TradeCommand
import trading.core.{ Conflicts, TradeEngine }
import trading.core.dedup.DedupRegistry
import trading.domain.EventId
import trading.events.TradeEvent
import trading.lib.*
import trading.state.{ DedupState, TradeState }

import cats.MonadThrow
import cats.syntax.all.*

object Engine:
  def fsm[F[_]: GenUUID: Logger: MonadThrow: Time](
      producer: Producer[F, TradeEvent],
      registry: DedupRegistry[F],
      ack: Consumer.MsgId => F[Unit]
  ): FSM[F, (TradeState, DedupState), Consumer.Msg[TradeCommand], Unit] =
    FSM { case ((st, ds), Consumer.Msg(msgId, command)) =>
      Conflicts.dedup(ds)(command) match
        case None =>
          Logger[F].warn(s"Deduplicated Command ID: ${command.id.show}").tupleLeft(st -> ds)
        case Some(cmd) =>
          val (nst, event) = TradeEngine.fsm.run(st, cmd)
          for
            evt <- (GenUUID[F].make[EventId], Time[F].timestamp).mapN(event)
            ecs = TradeEvent._Command.get(evt).toList
            nds <- Time[F].timestamp.map(Conflicts.updateMany(ds)(ecs, _))
            _   <- producer.send(evt)
            _   <- registry.save(nds)
            _ <- ack(msgId).attempt.void // don't care if this fails (de-dup)
          yield (nst -> nds) -> ()
    }
