package trading.alerts

import trading.commands.TradeCommand
import trading.core.{ Conflicts, TradeEngine }
import trading.domain.Alert.{ TradeAlert, TradeUpdate }
import trading.domain.AlertType.*
import trading.domain.*
import trading.events.TradeEvent
import trading.lib.*
import trading.state.{ DedupState, TradeState }

import cats.MonadThrow
import cats.syntax.all.*

object Engine:
  def fsm[F[_]: GenUUID: Logger: MonadThrow: Time](
      producer: Producer[F, Alert],
      ack: Consumer.MsgId => F[Unit]
  ): FSM[F, (TradeState, DedupState), Consumer.Msg[TradeEvent], Unit] =
    FSM {
      case ((st, ds), Consumer.Msg(msgId, TradeEvent.Started(_, cid, _))) =>
        (GenUUID[F].make[AlertId], Time[F].timestamp).tupled.flatMap { (id, ts) =>
          val alert = TradeUpdate(id, cid, TradingStatus.On, ts)
          (producer.send(alert) >> ack(msgId)).attempt.void.tupleLeft(st -> ds)
        }
      case ((st, ds), Consumer.Msg(msgId, TradeEvent.Stopped(_, cid, _))) =>
        (GenUUID[F].make[AlertId], Time[F].timestamp).tupled.flatMap { (id, ts) =>
          val alert = TradeUpdate(id, cid, TradingStatus.Off, ts)
          (producer.send(alert) >> ack(msgId)).attempt.void.tupleLeft(st -> ds)
        }
      case ((st, ds), Consumer.Msg(msgId, TradeEvent.CommandRejected(_, _, _, _, _))) =>
        ack(msgId).tupleLeft(st -> ds)
      case ((st, ds), Consumer.Msg(msgId, TradeEvent.CommandExecuted(_, cid, command, _))) =>
        Conflicts.dedup(ds)(command) match
          case None =>
            Logger[F].warn(s"Deduplicated Command ID: ${command.id.show}").tupleLeft(st -> ds)
          case Some(cmd) =>
            TradeCommand._Symbol.get(cmd).fold(ack(msgId).attempt.void.tupleLeft(st -> ds)) { symbol =>
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
              def alert(id: AlertId, ts: Timestamp): Alert =
                if previousAskMax - currentAskMax > Price(0.3) then
                  TradeAlert(id, cid, StrongBuy, symbol, currentAskMax, currentBidMax, high, low, ts)
                else if previousAskMax - currentAskMax > Price(0.2) then
                  TradeAlert(id, cid, Buy, symbol, currentAskMax, currentBidMax, high, low, ts)
                else if currentBidMax - previousBidMax > Price(0.3) then
                  TradeAlert(id, cid, StrongSell, symbol, currentAskMax, currentBidMax, high, low, ts)
                else if currentBidMax - previousBidMax > Price(0.2) then
                  TradeAlert(id, cid, Sell, symbol, currentAskMax, currentBidMax, high, low, ts)
                else
                  TradeAlert(id, cid, Neutral, symbol, currentAskMax, currentBidMax, high, low, ts)

              (GenUUID[F].make[AlertId], Time[F].timestamp).tupled.flatMap { (id, ts) =>
                val nds = Conflicts.update(ds)(cmd, ts)
                (producer.send(alert(id, ts)) >> ack(msgId)).attempt.void.tupleLeft(nst -> nds)
              }
            }
    }
