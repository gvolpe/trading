package trading.core

import cats.Id

import trading.commands.TradeCommand
import trading.domain.*
import trading.events.TradeEvent
import trading.lib.FSM
import trading.state.TradeState

object TradeEngine:
  val fsm: FSM[Id, TradeState, TradeCommand, List[(EventId, Timestamp) => TradeEvent]] =
    FSM.identity {
      case (st, cmd @ TradeCommand.Create(_, symbol, action, price, quantity, _, _)) =>
        val nst = st.modify(symbol)(action, price, quantity)
        nst -> List((id, ts) => TradeEvent.CommandExecuted(id, cmd, ts))
      case (st, cmd @ TradeCommand.Update(_, symbol, action, price, quantity, _, _)) =>
        val nst = st.modify(symbol)(action, price, quantity)
        nst -> List((id, ts) => TradeEvent.CommandExecuted(id, cmd, ts))
      case (st, cmd @ TradeCommand.Delete(_, symbol, action, price, _, _)) =>
        val nst = st.remove(symbol)(action, price)
        nst -> List((id, ts) => TradeEvent.CommandExecuted(id, cmd, ts))
    }
