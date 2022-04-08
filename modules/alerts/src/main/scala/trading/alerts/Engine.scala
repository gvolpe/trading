package trading.alerts

import trading.commands.TradeCommand
import trading.core.TradeEngine
import trading.domain.Alert.{ TradeAlert, TradeUpdate }
import trading.domain.AlertType.*
import trading.domain.TradingStatus.*
import trading.domain.*
import trading.events.*
import trading.lib.*
import trading.lib.Consumer.{ Msg, MsgId }
import trading.state.TradeState

import cats.MonadThrow
import cats.syntax.all.*

object Engine:
  type In = Either[Msg[TradeEvent], Msg[SwitchEvent]]

  def fsm[F[_]: GenUUID: Logger: MonadThrow: Time](
      producer: Producer[F, Alert],
      tradeAcker: Acker[F, TradeEvent],
      switchAcker: Acker[F, SwitchEvent]
  ): FSM[F, TradeState, In, Unit] =
    def mkIdTs: F[(AlertId, Timestamp)] =
      (GenUUID[F].make[AlertId], Time[F].timestamp).tupled

    def sendAck(alert: Alert, ack: F[Unit]): F[Unit] =
      (producer.send(alert) *> ack).attempt.void

    def switch(cid: CorrelationId, msgId: MsgId, nst: TradeState): F[(TradeState, Unit)] =
      mkIdTs.map(TradeUpdate(_, cid, nst.status, _)).flatMap { alert =>
        sendAck(alert, switchAcker.ack(msgId)).tupleLeft(nst)
      }

    FSM {
      // switch events
      case (st, Right(Msg(msgId, _, SwitchEvent.Started(_, cid, _)))) =>
        switch(cid, msgId, TradeState._Status.replace(On)(st))
      case (st, Right(Msg(msgId, _, SwitchEvent.Stopped(_, cid, _)))) =>
        switch(cid, msgId, TradeState._Status.replace(Off)(st))
      // no alert emitted, just ack the message
      case (st, Right(Msg(msgId, _, SwitchEvent.Ignored(_, _, _)))) =>
        switchAcker.ack(msgId).tupleLeft(st)
      case (st, Left(Msg(msgId, _, TradeEvent.CommandRejected(_, _, _, _, _)))) =>
        tradeAcker.ack(msgId).tupleLeft(st)
      // send price alert accordingly
      case (st, Left(Msg(msgId, _, TradeEvent.CommandExecuted(_, cid, cmd, _)))) =>
        val nst = TradeEngine.fsm.runS(st, cmd)
        val p   = st.prices.get(cmd.symbol)
        val c   = nst.prices.get(cmd.symbol)

        val previousAskMax: AskPrice = p.flatMap(_.ask.keySet.maxOption).getOrElse(Price(0.0))
        val previousBidMax: BidPrice = p.flatMap(_.bid.keySet.maxOption).getOrElse(Price(0.0))
        val currentAskMax: AskPrice  = c.flatMap(_.ask.keySet.maxOption).getOrElse(Price(0.0))
        val currentBidMax: BidPrice  = c.flatMap(_.bid.keySet.maxOption).getOrElse(Price(0.0))

        val high: Price = c.map(_.high).getOrElse(Price(0.0))
        val low: Price  = c.map(_.low).getOrElse(Price(0.0))

        // dummy logic to simulate the trading market
        def mkAlert(id: AlertId, ts: Timestamp): Alert =
          if previousAskMax - currentAskMax > Price(0.3) then
            TradeAlert(id, cid, StrongBuy, cmd.symbol, currentAskMax, currentBidMax, high, low, ts)
          else if previousAskMax - currentAskMax > Price(0.2) then
            TradeAlert(id, cid, Buy, cmd.symbol, currentAskMax, currentBidMax, high, low, ts)
          else if currentBidMax - previousBidMax > Price(0.3) then
            TradeAlert(id, cid, StrongSell, cmd.symbol, currentAskMax, currentBidMax, high, low, ts)
          else if currentBidMax - previousBidMax > Price(0.2) then
            TradeAlert(id, cid, Sell, cmd.symbol, currentAskMax, currentBidMax, high, low, ts)
          else TradeAlert(id, cid, Neutral, cmd.symbol, currentAskMax, currentBidMax, high, low, ts)

        mkIdTs.map(mkAlert).flatMap { alert =>
          sendAck(alert, tradeAcker.ack(msgId)).tupleLeft(nst)
        }
    }
