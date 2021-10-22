package trading.core

import trading.commands.TradeCommand
import trading.domain.*
import trading.events.TradeEvent
import trading.state.TradeState

object EventSource:
  def run(st: TradeState)(command: TradeCommand): (TradeState, List[(EventId, Timestamp) => TradeEvent]) =
    command match
      case cmd @ TradeCommand.Create(_, symbol, action, price, quantity, _, _) =>
        val nst = st.modify(symbol)(action, price, quantity)
        nst -> List((id, ts) => TradeEvent.CommandExecuted(id, cmd, ts))
      case cmd @ TradeCommand.Update(_, symbol, action, price, quantity, _, _) =>
        val nst = st.modify(symbol)(action, price, quantity)
        nst -> List((id, ts) => TradeEvent.CommandExecuted(id, cmd, ts))
      case cmd @ TradeCommand.Delete(_, symbol, action, price, _, _) =>
        val nst = st.remove(symbol)(action, price)
        nst -> List((id, ts) => TradeEvent.CommandExecuted(id, cmd, ts))

  def runS(st: TradeState)(command: TradeCommand): TradeState =
    run(st)(command)._1
