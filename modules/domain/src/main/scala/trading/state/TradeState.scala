package trading.state

import trading.domain._

import monocle.function.Index
import monocle.{Focus, Optional}

final case class TradeState(
    prices: TradeState.SymbolPricesMap
) {
  def modifyAsk(symbol: Symbol)(price: AskPrice): TradeState =
    TradeState
      .__AskPrices(symbol)
      .modify(_ :+ price)(this)

  def modifyBid(symbol: Symbol)(price: BidPrice): TradeState =
    TradeState
      .__BidPrices(symbol)
      .modify(_ :+ price)(this)

  def removePrice(symbol: Symbol)(action: TradeAction, price: Price): TradeState =
    action match {
      case TradeAction.Ask =>
        TradeState
          .__AskPrices(symbol)
          .modify(_.filterNot(_ == price))(this)
      case TradeAction.Bid =>
        TradeState
          .__BidPrices(symbol)
          .modify(_.filterNot(_ == price))(this)
    }
}

object TradeState {
  type Prices          = (List[AskPrice], List[BidPrice])
  type SymbolPricesMap = Map[Symbol, Prices]

  def empty: TradeState = TradeState(Map.empty)

  val __Prices: Symbol => Optional[TradeState, Prices] = s =>
    Focus[TradeState](_.prices).andThen(Index.mapIndex[Symbol, Prices].index(s))

  val __AskPrices: Symbol => Optional[TradeState, List[AskPrice]] =
    __Prices(_).andThen(Focus[Prices](_._1))

  val __BidPrices: Symbol => Optional[TradeState, List[BidPrice]] =
    __Prices(_).andThen(Focus[Prices](_._2))

  //val __Prices: Symbol => Lens[TradeState, Option[Prices]] = s =>
  //_Prices.andThen(At.atMap[Symbol, Prices].at(s))

  //TradeState
  //.__Prices(symbol)
  //.modifyOption { case (_, bp) => price -> bp }(this)
  //.getOrElse(TradeState._Prices.modify(_.updated(symbol, price -> BigDecimal(0.0)))(this))
  //
  //TradeState
  //.__Prices(symbol)
  //.modify {
  //case Some((ask, _)) => Some(ask -> bid)
  //case None           => Some(BigDecimal(0.0) -> bid)
  //}(this)
}
