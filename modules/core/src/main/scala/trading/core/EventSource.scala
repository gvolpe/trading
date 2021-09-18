package trading.core

import trading.commands.TradeCommand
import trading.domain.{ Timestamp, TradeAction }
import trading.events.TradeEvent
import trading.state.TradeState

object EventSource {
  def run(st: TradeState)(command: TradeCommand): (TradeState, List[Timestamp => TradeEvent]) =
    command match {
      case cmd @ TradeCommand.Create(symbol, TradeAction.Ask, price, _, _, _) =>
        //TODO: use lenses
        val prices = st.prices.updatedWith(symbol) {
          case Some((_, bp)) => Some(price -> bp)
          case None          => Some(price -> BigDecimal(0.0))
        }
        val newSt = TradeState(prices)
        newSt -> List(ts => TradeEvent.CommandExecuted(cmd, ts))
      case TradeCommand.Create(symbol, TradeAction.Bid, price, _, _, _) =>
        st -> List.empty
      case (cmd: TradeCommand.Update) =>
        st -> List.empty
      case (cmd: TradeCommand.Delete) =>
        st -> List.empty
    }

  def runS(st: TradeState)(command: TradeCommand): TradeState =
    run(st)(command)._1
}
