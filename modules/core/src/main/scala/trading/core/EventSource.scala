package trading.core

import trading.commands.TradeCommand
import trading.domain.Timestamp
import trading.events.TradeEvent
import trading.state.TradeState

object EventSource {
  def run(st: TradeState)(command: TradeCommand): (TradeState, List[Timestamp => TradeEvent]) =
    command match {
      case cmd @ TradeCommand.Create(symbol, action, price, quantity, _, _) =>
        val newSt = st.modify(symbol)(action, price, quantity)
        newSt -> List(ts => TradeEvent.CommandExecuted(cmd, ts))
      case cmd @ TradeCommand.Update(symbol, action, price, quantity, _, _) =>
        val newSt = st.modify(symbol)(action, price, quantity)
        newSt -> List(ts => TradeEvent.CommandExecuted(cmd, ts))
      case cmd @ TradeCommand.Delete(symbol, action, price, _, _) =>
        val newSt = st.remove(symbol)(action, price)
        newSt -> List(ts => TradeEvent.CommandExecuted(cmd, ts))
    }

  def runS(st: TradeState)(command: TradeCommand): TradeState =
    run(st)(command)._1
}
