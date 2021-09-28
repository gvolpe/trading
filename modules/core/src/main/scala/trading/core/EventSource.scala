package trading.core

import trading.commands.TradeCommand
import trading.domain.Timestamp
import trading.events.TradeEvent
import trading.state.{ DedupState, TradeState }

import cats.syntax.all._

object EventSource {
  def run(st: TradeState)(command: TradeCommand): (TradeState, List[Timestamp => TradeEvent]) =
    command match {
      case cmd @ TradeCommand.Create(_, symbol, action, price, quantity, _, _) =>
        val nst = st.modify(symbol)(action, price, quantity)
        nst -> List(ts => TradeEvent.CommandExecuted(cmd, ts))
      case cmd @ TradeCommand.Update(_, symbol, action, price, quantity, _, _) =>
        val nst = st.modify(symbol)(action, price, quantity)
        nst -> List(ts => TradeEvent.CommandExecuted(cmd, ts))
      case cmd @ TradeCommand.Delete(_, symbol, action, price, _, _) =>
        val nst = st.remove(symbol)(action, price)
        nst -> List(ts => TradeEvent.CommandExecuted(cmd, ts))
    }

  def runS(st: TradeState)(command: TradeCommand): TradeState =
    run(st)(command)._1

  def dedup(st: DedupState)(command: TradeCommand): Option[TradeCommand] =
    st.ids.map(_.id).contains(command.id).guard[Option].as(command)
}
