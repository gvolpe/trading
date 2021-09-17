package trading.state

import trading.domain._

final case class TradeState(
    prices: Map[Symbol, Prices]
)

object TradeState {
  def empty: TradeState = TradeState(Map.empty)
}

final case class Prices(
    ask: Map[PriceLevel, Price],
    bid: Map[PriceLevel, Price]
)
