package trading.alerts

import trading.core.{ Conflicts, EventSource }
import trading.domain.AlertType.*
import trading.domain.*
import trading.events.TradeEvent
import trading.events.TradeEvent.CommandExecuted
import trading.lib.*
import trading.state.{ DedupState, TradeState }

import cats.MonadThrow
import cats.syntax.all.*

object Engine:
  def fsm[F[_]: Logger: MonadThrow: Time](
      producer: Producer[F, Alert],
      ack: Consumer.MsgId => F[Unit]
  ): FSM[F, (TradeState, DedupState), Consumer.Msg[TradeEvent], Unit] =
    FSM { case ((st, ds), Consumer.Msg(msgId, CommandExecuted(_, command, _))) =>
      Conflicts.dedup(ds)(command) match
        case None =>
          Logger[F].warn(s"Deduplicated Command ID: ${command.id.show}").tupleLeft(st -> ds)
        case Some(cmd) =>
          val nst = EventSource.runS(st)(cmd)
          val p   = st.prices.get(cmd.symbol)
          val c   = nst.prices.get(cmd.symbol)

          val previousAskMax: AskPrice = p.flatMap(_.ask.keySet.maxOption).getOrElse(Price(0.0))
          val previousBidMax: BidPrice = p.flatMap(_.bid.keySet.maxOption).getOrElse(Price(0.0))
          val currentAskMax: AskPrice  = c.flatMap(_.ask.keySet.maxOption).getOrElse(Price(0.0))
          val currentBidMax: BidPrice  = c.flatMap(_.bid.keySet.maxOption).getOrElse(Price(0.0))

          val high: Price = c.map(_.high).getOrElse(Price(0.0))
          val low: Price  = c.map(_.low).getOrElse(Price(0.0))

          // dummy logic to simulate the trading market
          val alert: Alert =
            if (previousAskMax - currentAskMax > Price(0.3))
              Alert(StrongBuy, cmd.symbol, currentAskMax, currentBidMax, high, low)
            else if (previousAskMax - currentAskMax > Price(0.2))
              Alert(Buy, cmd.symbol, currentAskMax, currentBidMax, high, low)
            else if (currentBidMax - previousBidMax > Price(0.3))
              Alert(StrongSell, cmd.symbol, currentAskMax, currentBidMax, high, low)
            else if (currentBidMax - previousBidMax > Price(0.2))
              Alert(Sell, cmd.symbol, currentAskMax, currentBidMax, high, low)
            else
              Alert(Neutral, cmd.symbol, currentAskMax, currentBidMax, high, low)

          Time[F].timestamp.flatMap { ts =>
            val nds = Conflicts.update(ds)(cmd, ts)
            (producer.send(alert) >> ack(msgId)).attempt.void.tupleLeft(nst -> nds)
          }
    }
