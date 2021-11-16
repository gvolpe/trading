package trading.core

import trading.commands.TradeCommand
import trading.domain.TradingStatus.*
import trading.domain.*
import trading.events.TradeEvent
import trading.lib.FSM
import trading.state.TradeState

import cats.Id

object TradeEngine:
  val fsm: FSM[Id, TradeState, TradeCommand, (EventId, Timestamp) => TradeEvent] =
    FSM.id {
      // Trading status: On
      case (st @ TradeState(On, _), cmd @ TradeCommand.Create(_, cid, symbol, action, price, quantity, _, _)) =>
        val nst = st.modify(symbol)(action, price, quantity)
        nst -> ((id, ts) => TradeEvent.CommandExecuted(id, cid, cmd, ts))
      case (st @ TradeState(On, _), cmd @ TradeCommand.Update(_, cid, symbol, action, price, quantity, _, _)) =>
        val nst = st.modify(symbol)(action, price, quantity)
        nst -> ((id, ts) => TradeEvent.CommandExecuted(id, cid, cmd, ts))
      case (st @ TradeState(On, _), cmd @ TradeCommand.Delete(_, cid, symbol, action, price, _, _)) =>
        val nst = st.remove(symbol)(action, price)
        nst -> ((id, ts) => TradeEvent.CommandExecuted(id, cid, cmd, ts))
      // Trading switch: On / Off
      case (st, TradeCommand.Start(_, cid, _)) =>
        val nst = TradeState._Status.replace(On)(st)
        nst -> ((id, ts) => TradeEvent.Started(id, cid, ts))
      case (st, TradeCommand.Stop(_, cid, _)) =>
        val nst = TradeState._Status.replace(Off)(st)
        nst -> ((id, ts) => TradeEvent.Stopped(id, cid, ts))
      // Trading status: Off
      case (st @ TradeState(Off, _), cmd) =>
        st -> ((id, ts) => TradeEvent.CommandRejected(id, cmd.cid, cmd, Reason("Trading is Off"), ts))
    }
