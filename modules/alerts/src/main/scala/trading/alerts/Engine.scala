package trading.alerts

import trading.core.TradeEngine
import trading.domain.Alert.{ TradeAlert, TradeUpdate }
import trading.domain.AlertType.*
import trading.domain.*
import trading.events.*
import trading.lib.*
import trading.lib.Consumer.{ Msg, MsgId }
import trading.state.TradeState

import cats.effect.kernel.{ MonadCancelThrow, Resource }
import cats.syntax.all.*

object Engine:
  type In = Msg[TradeEvent | SwitchEvent | PriceUpdate]

  def fsm[F[_]: GenUUID: Logger: MonadCancelThrow: Time](
      appId: AppId,
      alertProducer: Producer[F, Alert],
      pricesProducer: Producer[F, PriceUpdate],
      pulsarTx: Resource[F, Txn],
      tradeAcker: Acker[F, TradeEvent],
      switchAcker: Acker[F, SwitchEvent],
      pricesAcker: Acker[F, PriceUpdate]
  ): FSM[F, TradeState, In, Unit] =
    def mkIdTs: F[(AlertId, Timestamp)] =
      (GenUUID[F].make[AlertId], Time[F].timestamp).tupled

    def sendAck(alert: Alert, priceUpdate: Option[PriceUpdate], ack: Txn => F[Unit]): F[Unit] =
      pulsarTx.use { tx =>
        for
          _ <- alertProducer.send(alert, tx)
          _ <- priceUpdate.traverse_(pricesProducer.send(_, Map("app-id" -> appId.id.show), tx))
          _ <- ack(tx)
        yield ()
      }

    def switch(cid: CorrelationId, msgId: MsgId, st: TradeState, nst: TradeState): F[(TradeState, Unit)] =
      mkIdTs.map(TradeUpdate(_, cid, nst.status, _)).flatMap { alert =>
        sendAck(alert, None, switchAcker.ack(msgId, _))
          .tupleLeft(nst)
          .handleErrorWith { e =>
            Logger[F].warn(s"Transaction failed: ${e.getMessage}").tupleLeft(st)
          }
      }

    FSM {
      // no alert emitted, just ack the message
      case (st, Msg(msgId, _, SwitchEvent.Ignored(_, _, _))) =>
        switchAcker.ack(msgId).tupleLeft(st)
      case (st, Msg(msgId, _, TradeEvent.CommandRejected(_, _, _, _, _))) =>
        tradeAcker.ack(msgId).tupleLeft(st)
      // switch started / stopped events
      case (st, Msg(msgId, _, evt: SwitchEvent)) =>
        switch(evt.cid, msgId, st, TradeEngine.eventsFsm.runS(st, evt))
      // price update
      case (st, Msg(msgId, props, PriceUpdate(symbol, prices))) =>
        val nst = props.get("app-id") match
          case Some(id) if id =!= appId.id.show =>
            TradeState.__Prices.at(symbol).replace(Some(prices))(st)
          case _ => st
        pricesAcker.ack(msgId).tupleLeft(nst)
      // send price alert accordingly
      case (st, Msg(msgId, _, evt: TradeEvent.CommandExecuted)) =>
        val nst = TradeEngine.eventsFsm.runS(st, evt)
        val cmd = evt.command
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
            TradeAlert(id, evt.cid, StrongBuy, cmd.symbol, currentAskMax, currentBidMax, high, low, ts)
          else if previousAskMax - currentAskMax > Price(0.2) then
            TradeAlert(id, evt.cid, Buy, cmd.symbol, currentAskMax, currentBidMax, high, low, ts)
          else if currentBidMax - previousBidMax > Price(0.3) then
            TradeAlert(id, evt.cid, StrongSell, cmd.symbol, currentAskMax, currentBidMax, high, low, ts)
          else if currentBidMax - previousBidMax > Price(0.2) then
            TradeAlert(id, evt.cid, Sell, cmd.symbol, currentAskMax, currentBidMax, high, low, ts)
          else TradeAlert(id, evt.cid, Neutral, cmd.symbol, currentAskMax, currentBidMax, high, low, ts)

        // if current symbol prices changed, send a price update
        val priceUpdate = c.flatMap { prices =>
          (p =!= c).guard[Option].as(PriceUpdate(cmd.symbol, prices))
        }

        mkIdTs.map(mkAlert).flatMap { alert =>
          sendAck(alert, priceUpdate, tradeAcker.ack(msgId, _))
            .tupleLeft(nst)
            .handleErrorWith { e =>
              Logger[F].warn(s"Transaction failed: ${e.getMessage}").tupleLeft(st)
            }
        }
    }
