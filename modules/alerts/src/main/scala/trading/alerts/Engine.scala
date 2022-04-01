package trading.alerts

import trading.commands.TradeCommand
import trading.core.TradeEngine
import trading.domain.Alert.{ TradeAlert, TradeUpdate }
import trading.domain.AlertType.*
import trading.domain.TradingStatus.*
import trading.domain.*
import trading.events.TradeEvent
import trading.lib.*
import trading.lib.Consumer.{ Msg, MsgId }
import trading.state.TradeState

import cats.MonadThrow
import cats.syntax.all.*

object Engine:
  def fsm[F[_]: GenUUID: Logger: MonadThrow: Time](
      producer: Producer[F, Alert],
      ack: Consumer.MsgId => F[Unit]
  ): FSM[F, TradeState, Consumer.Msg[TradeEvent], Unit] =
    def mkIdTs: F[(AlertId, Timestamp)] =
      (GenUUID[F].make[AlertId], Time[F].timestamp).tupled

    def sendAck(alert: Alert, msgId: MsgId): F[Unit] =
      (producer.send(alert) *> ack(msgId)).attempt.void

    FSM {
      case (st @ TradeState(Off, _), Msg(msgId, _, TradeEvent.Started(_, cid, _))) =>
        mkIdTs.map(TradeUpdate(_, cid, On, _)).flatMap { alert =>
          val nst = TradeState._Status.replace(On)(st)
          sendAck(alert, msgId).tupleLeft(nst)
        }
      case (st @ TradeState(On, _), Msg(msgId, _, TradeEvent.Stopped(_, cid, _))) =>
        mkIdTs.map(TradeUpdate(_, cid, Off, _)).flatMap { alert =>
          val nst = TradeState._Status.replace(Off)(st)
          sendAck(alert, msgId).tupleLeft(nst)
        }
      case (st @ TradeState(On, _), Msg(msgId, _, TradeEvent.Started(_, _, _))) =>
        (Logger[F].warn(s"Status already On") *> ack(msgId)).tupleLeft(st)
      case (st @ TradeState(Off, _), Msg(msgId, _, TradeEvent.Stopped(_, _, _))) =>
        (Logger[F].warn(s"Status already Off") *> ack(msgId)).tupleLeft(st)
      case (st, Msg(msgId, _, TradeEvent.CommandRejected(_, _, _, _, _))) =>
        ack(msgId).tupleLeft(st)
      case (st, Msg(msgId, _, TradeEvent.CommandExecuted(_, cid, cmd, _))) =>
        TradeCommand._Symbol.get(cmd).fold(ack(msgId).attempt.void.tupleLeft(st)) { symbol =>
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
          def mkAlert(id: AlertId, ts: Timestamp): Alert =
            if previousAskMax - currentAskMax > Price(0.3) then
              TradeAlert(id, cid, StrongBuy, symbol, currentAskMax, currentBidMax, high, low, ts)
            else if previousAskMax - currentAskMax > Price(0.2) then
              TradeAlert(id, cid, Buy, symbol, currentAskMax, currentBidMax, high, low, ts)
            else if currentBidMax - previousBidMax > Price(0.3) then
              TradeAlert(id, cid, StrongSell, symbol, currentAskMax, currentBidMax, high, low, ts)
            else if currentBidMax - previousBidMax > Price(0.2) then
              TradeAlert(id, cid, Sell, symbol, currentAskMax, currentBidMax, high, low, ts)
            else TradeAlert(id, cid, Neutral, symbol, currentAskMax, currentBidMax, high, low, ts)

          mkIdTs.map(mkAlert).flatMap { alert =>
            sendAck(alert, msgId).tupleLeft(nst)
          }
        }
    }
