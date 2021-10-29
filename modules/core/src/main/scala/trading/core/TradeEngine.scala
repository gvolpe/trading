package trading.core

import trading.commands.TradeCommand
import trading.domain.TradingStatus.*
import trading.domain.*
import trading.events.TradeEvent
import trading.lib.FSM
import trading.state.TradeState

import cats.Id

object TradeEngine:
  val fsm: FSM[Id, TradeState, TradeCommand, List[(EventId, Timestamp) => TradeEvent]] =
    FSM.identity {
      // Trading status: On
      case (st @ TradeState(On, _), cmd @ TradeCommand.Create(_, symbol, action, price, quantity, _, _)) =>
        val nst = st.modify(symbol)(action, price, quantity)
        nst -> List((id, ts) => TradeEvent.CommandExecuted(id, cmd, ts))
      case (st @ TradeState(On, _), cmd @ TradeCommand.Update(_, symbol, action, price, quantity, _, _)) =>
        val nst = st.modify(symbol)(action, price, quantity)
        nst -> List((id, ts) => TradeEvent.CommandExecuted(id, cmd, ts))
      case (st @ TradeState(On, _), cmd @ TradeCommand.Delete(_, symbol, action, price, _, _)) =>
        val nst = st.remove(symbol)(action, price)
        nst -> List((id, ts) => TradeEvent.CommandExecuted(id, cmd, ts))
      // Trading switch: On / Off
      case (st, TradeCommand.Start(_, _)) =>
        val nst = TradeState._Status.replace(On)(st)
        nst -> List((id, ts) => TradeEvent.Started(id, ts))
      case (st, TradeCommand.Stop(_, _)) =>
        val nst = TradeState._Status.replace(Off)(st)
        nst -> List((id, ts) => TradeEvent.Stopped(id, ts))
      // Trading status: Off
      case (st @ TradeState(Off, _), cmd) =>
        st -> List((id, ts) => TradeEvent.CommandRejected(id, cmd, Reason("Trading is Off"), ts))
    }
