package trading.state

import trading.domain._

import cats.syntax.all._
import derevo.cats._
import derevo.derive
import monocle.Optional
import monocle.function.At
import monocle.macros.GenLens

@derive(eqv, show)
final case class TradeState(
    prices: TradeState.SymbolPrices
) {
  def modify(symbol: Symbol)(action: TradeAction, price: Price, quantity: Quantity): TradeState =
    action match {
      case TradeAction.Ask =>
        val f = Prices._Ask.modify(_.updated(price, quantity))(_)
        TradeState.__Prices(symbol).modify(_.fold(f(Prices.empty))(f).some)(this)
      case TradeAction.Bid =>
        val f = Prices._Bid.modify(_.updated(price, quantity))(_)
        TradeState.__Prices(symbol).modify(_.fold(f(Prices.empty))(f).some)(this)
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

@derive(eqv, show)
final case class Prices(
    ask: Prices.Ask,
    bid: Prices.Bid
)

object Prices {
  def empty: Prices = Prices(Map.empty, Map.empty)

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
    def apply(s: Symbol): Optional[TradeState, Option[Prices]] =
      _Prices.andThen(At.atMap[Symbol, Prices].at(s))
  }

  object __AskPrices {
    def apply(s: Symbol): Optional[TradeState, Prices.Ask] =
      __Prices(s).andThen(monocle.std.option.some[Prices]).andThen(Prices._Ask)
  }

  object __BidPrices {
    def apply(s: Symbol): Optional[TradeState, Prices.Bid] =
      __Prices(s).andThen(monocle.std.option.some[Prices]).andThen(Prices._Bid)
  }

  object __AskQuantity {
    def apply(s: Symbol, p: Price): Optional[TradeState, Option[Quantity]] =
      __Prices(s).andThen(monocle.std.option.some[Prices]).andThen(Prices.__AtAsk(p))
  }

  object __BidQuantity {
    def apply(s: Symbol, p: Price): Optional[TradeState, Option[Quantity]] =
      __Prices(s).andThen(monocle.std.option.some[Prices]).andThen(Prices.__AtBid(p))
  }
}
