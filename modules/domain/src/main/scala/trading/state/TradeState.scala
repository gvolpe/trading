package trading.state

import trading.domain._

import cats.syntax.all._
import monocle.Optional
import monocle.function.{ At, Index }
import monocle.macros.GenLens

final case class TradeState(
    prices: TradeState.SymbolPrices
) {
  def modify(symbol: Symbol)(action: TradeAction, price: Price, quantity: Quantity): TradeState =
    action match {
      case TradeAction.Ask =>
        TradeState
          .__AskQuantity(symbol, price)
          .modify(_.fold(quantity)(_ + quantity).some)(this)
      case TradeAction.Bid =>
        TradeState
          .__BidQuantity(symbol, price)
          .modify(_.fold(quantity)(_ + quantity).some)(this)
    }

  def remove(symbol: Symbol)(action: TradeAction, price: Price): TradeState =
    action match {
      case TradeAction.Ask =>
        TradeState
          .__AskPrices(symbol)
          .modify(_.removed(price))(this)
      case TradeAction.Bid =>
        TradeState
          .__BidPrices(symbol)
          .modify(_.removed(price))(this)
    }
}

final case class Prices(
    ask: Prices.Ask,
    bid: Prices.Bid
)

object Prices {
  type Ask = Map[AskPrice, Quantity]
  type Bid = Map[BidPrice, Quantity]

  val _Ask = GenLens[Prices](_.ask)
  val _Bid = GenLens[Prices](_.bid)

  object __AtAsk {
    def apply(p: AskPrice): Optional[Prices, Option[Quantity]] =
      _Ask.andThen(At.atMap[AskPrice, Quantity].at(p))
  }

  object __AtBid {
    def apply(p: BidPrice): Optional[Prices, Option[Quantity]] =
      _Bid.andThen(At.atMap[BidPrice, Quantity].at(p))
  }
}

object TradeState {
  type SymbolPrices = Map[Symbol, Prices]

  def empty: TradeState = TradeState(Map.empty)

  val _Prices = GenLens[TradeState](_.prices)

  object __Prices {
    def apply(s: Symbol): Optional[TradeState, Prices] =
      _Prices.andThen(Index.mapIndex[Symbol, Prices].index(s))
  }

  object __AskPrices {
    def apply(s: Symbol): Optional[TradeState, Prices.Ask] =
      __Prices(s).andThen(Prices._Ask)
  }

  object __BidPrices {
    def apply(s: Symbol): Optional[TradeState, Prices.Bid] =
      __Prices(s).andThen(Prices._Bid)
  }

  object __AskQuantity {
    def apply(s: Symbol, p: Price): Optional[TradeState, Option[Quantity]] =
      __Prices(s).andThen(Prices.__AtAsk(p))
  }

  object __BidQuantity {
    def apply(s: Symbol, p: Price): Optional[TradeState, Option[Quantity]] =
      __Prices(s).andThen(Prices.__AtBid(p))
  }
}
