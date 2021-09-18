package trading.state

import trading.domain._

final case class TradeState(
    prices: Map[Symbol, (AskPrice, BidPrice)]
)

object TradeState {
  def empty: TradeState = TradeState(Map.empty)
}
