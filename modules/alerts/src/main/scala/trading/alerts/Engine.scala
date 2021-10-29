package trading.alerts

import trading.commands.TradeCommand
import trading.core.{ Conflicts, TradeEngine }
import trading.domain.AlertType.*
import trading.domain.*
import trading.events.TradeEvent
import trading.lib.*
import trading.state.{ DedupState, TradeState }

import cats.MonadThrow
import cats.syntax.all.*

object Engine:
  def fsm[F[_]: Logger: MonadThrow: Time](
      producer: Producer[F, Alert],
      ack: Consumer.MsgId => F[Unit]
  ): FSM[F, (TradeState, DedupState), Consumer.Msg[TradeEvent], Unit] =
    FSM {
      // TODO: decide what to do on Started and Stopped / Rejected
      case ((st, ds), Consumer.Msg(msgId, TradeEvent.Started(_, _))) =>
        ().pure[F].tupleLeft(st -> ds)
      case ((st, ds), Consumer.Msg(msgId, TradeEvent.Stopped(_, _))) =>
        ().pure[F].tupleLeft(st -> ds)
      case ((st, ds), Consumer.Msg(msgId, TradeEvent.CommandRejected(_, _, _, _))) =>
        ().pure[F].tupleLeft(st -> ds)
      case ((st, ds), Consumer.Msg(msgId, TradeEvent.CommandExecuted(_, command, _))) =>
        Conflicts.dedup(ds)(command) match
          case None =>
            Logger[F].warn(s"Deduplicated Command ID: ${command.id.show}").tupleLeft(st -> ds)
          case Some(cmd) =>
            TradeCommand._Symbol.get(cmd) match
              case Some(symbol) =>
                val nst = TradeEngine.fsm.runS(st, cmd)
                val p   = st.prices.get(symbol)
                val c   = nst.prices.get(symbol)

                val previousAskMax: AskPrice = p.flatMap(_.ask.keySet.maxOption).getOrElse(Price(0.0))
                val previousBidMax: BidPrice = p.flatMap(_.bid.keySet.maxOption).getOrElse(Price(0.0))
                val currentAskMax: AskPrice  = c.flatMap(_.ask.keySet.maxOption).getOrElse(Price(0.0))
                val currentBidMax: BidPrice  = c.flatMap(_.bid.keySet.maxOption).getOrElse(Price(0.0))

                val high: Price = c.map(_.high).getOrElse(Price(0.0))
                val low: Price  = c.map(_.low).getOrElse(Price(0.0))

                // dummy logic to simulate the trading market
                val alert: Alert =
                  if (previousAskMax - currentAskMax > Price(0.3))
                    Alert(StrongBuy, symbol, currentAskMax, currentBidMax, high, low)
                  else if (previousAskMax - currentAskMax > Price(0.2))
                    Alert(Buy, symbol, currentAskMax, currentBidMax, high, low)
                  else if (currentBidMax - previousBidMax > Price(0.3))
                    Alert(StrongSell, symbol, currentAskMax, currentBidMax, high, low)
                  else if (currentBidMax - previousBidMax > Price(0.2))
                    Alert(Sell, symbol, currentAskMax, currentBidMax, high, low)
                  else
                    Alert(Neutral, symbol, currentAskMax, currentBidMax, high, low)

                Time[F].timestamp.flatMap { ts =>
                  val nds = Conflicts.update(ds)(cmd, ts)
                  (producer.send(alert) >> ack(msgId)).attempt.void.tupleLeft(nst -> nds)
                }
              case None =>
                Logger[F].warn("s").tupleLeft(st -> ds)
    }
