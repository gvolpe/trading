package trading.core

import trading.commands.TradeCommand
import trading.domain.{ Timestamp, TradeAction }
import trading.events.TradeEvent
import trading.state.TradeState

object EventSource {
  def run(st: TradeState)(command: TradeCommand): (TradeState, List[Timestamp => TradeEvent]) =
    command match {
      case cmd @ TradeCommand.Add(symbol, TradeAction.Ask, price, _, _, _) =>
        val newSt = st.modifyAsk(symbol)(price)
        newSt -> List(ts => TradeEvent.CommandExecuted(cmd, ts))
      case cmd @ TradeCommand.Add(symbol, TradeAction.Bid, price, _, _, _) =>
        val newSt = st.modifyBid(symbol)(price)
        newSt -> List(ts => TradeEvent.CommandExecuted(cmd, ts))
      case cmd @ TradeCommand.Delete(symbol, action, price, _, _) =>
        val newSt = st.removePrice(symbol)(action, price)
        newSt -> List(ts => TradeEvent.CommandExecuted(cmd, ts))
    }

  def runS(st: TradeState)(command: TradeCommand): TradeState =
    run(st)(command)._1
}
