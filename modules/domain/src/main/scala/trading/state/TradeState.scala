package trading.state

import trading.domain.*

import cats.syntax.all.*
import cats.{ Eq, Show }
import io.circe.Codec
import monocle.function.{ At, Index }
import monocle.{ Focus, Optional }

final case class TradeState(
    prices: TradeState.SymbolPrices
):
  def modify(symbol: Symbol)(action: TradeAction, price: Price, quantity: Quantity): TradeState = {
    val h = Prices._High.modify(p => if (price > p) price else p)(_)
    val l = Prices._Low.modify(p => if (price < p || p === 0.0) price else p)(_)

    action match {
      case TradeAction.Ask =>
        val f = Prices._Ask.modify(_.updated(price, quantity))(_)
        val g = f.andThen(h).andThen(l)
        TradeState.__Prices.at(symbol).modify(_.fold(g(Prices.empty))(g).some)(this)
      case TradeAction.Bid =>
        val f = Prices._Bid.modify(_.updated(price, quantity))(_)
        val g = f.andThen(h).andThen(l)
        TradeState.__Prices.at(symbol).modify(_.fold(g(Prices.empty))(g).some)(this)
    }
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

final case class Prices(
    ask: Prices.Ask,
    bid: Prices.Bid,
    high: Price,
    low: Price
)

object Prices:
  given Eq[Prices] = Eq.and(Eq.and(Eq.by(_.ask), Eq.by(_.bid)), Eq.and(Eq.by(_.high), Eq.by(_.low)))

  def empty: Prices = Prices(Map.empty, Map.empty, 0.0, 0.0)

  type Ask = Map[AskPrice, Quantity]
  type Bid = Map[BidPrice, Quantity]

  val _Ask  = Focus[Prices](_.ask)
  val _Bid  = Focus[Prices](_.bid)
  val _High = Focus[Prices](_.high)
  val _Low  = Focus[Prices](_.low)

  object __AtAsk:
    def apply(p: AskPrice): Optional[Prices, Option[Quantity]] =
      _Ask.andThen(At.atMap[AskPrice, Quantity].at(p))

  object __AtBid:
    def apply(p: BidPrice): Optional[Prices, Option[Quantity]] =
      _Bid.andThen(At.atMap[BidPrice, Quantity].at(p))
end Prices

object TradeState:
  type SymbolPrices = Map[Symbol, Prices]

  def empty: TradeState = TradeState(Map.empty)

  given Eq[TradeState] = Eq.by(_.prices)

  val _Prices = Focus[TradeState](_.prices)

  object __Prices:
    def at(s: Symbol): Optional[TradeState, Option[Prices]] =
      _Prices.andThen(At.atMap[Symbol, Prices].at(s))

    def index(s: Symbol): Optional[TradeState, Prices] =
      _Prices.andThen(Index.mapIndex[Symbol, Prices].index(s))

  object __AskPrices:
    def apply(s: Symbol): Optional[TradeState, Prices.Ask] =
      __Prices.index(s).andThen(Prices._Ask)

  object __BidPrices:
    def apply(s: Symbol): Optional[TradeState, Prices.Bid] =
      __Prices.index(s).andThen(Prices._Bid)
end TradeState
