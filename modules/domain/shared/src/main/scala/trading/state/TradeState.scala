package trading.state

import trading.domain.*

import cats.derived.semiauto.*
import cats.syntax.all.*
import cats.{ Eq, Show }
import io.circe.*
import monocle.function.{ At, Index }
import monocle.{ Focus, Optional }

final case class TradeState(
    status: TradingStatus,
    prices: TradeState.SymbolPrices
) derives Eq, Show:
  def modify(symbol: Symbol)(action: TradeAction, price: Price, quantity: Quantity): TradeState = {
    val h = Prices._High.modify(p => if price > p then price else p)(_)
    val l = Prices._Low.modify(p => if price < p || p === Price(0.0) then price else p)(_)

    action match
      case TradeAction.Ask =>
        val f = Prices._Ask.modify(_.updated(price, quantity))(_)
        val g = f.andThen(h).andThen(l)
        TradeState.__Prices.at(symbol).modify(_.fold(g(Prices.empty))(g).some)(this)
      case TradeAction.Bid =>
        val f = Prices._Bid.modify(_.updated(price, quantity))(_)
        val g = f.andThen(h).andThen(l)
        TradeState.__Prices.at(symbol).modify(_.fold(g(Prices.empty))(g).some)(this)
  }

  def remove(symbol: Symbol)(action: TradeAction, price: Price): TradeState =
    action match
      case TradeAction.Ask =>
        TradeState
          .__AskPrices(symbol)
          .modify(_.removed(price))(this)
      case TradeAction.Bid =>
        TradeState
          .__BidPrices(symbol)
          .modify(_.removed(price))(this)

object TradeState:
  type SymbolPrices = Map[Symbol, Prices]

  def empty: TradeState = TradeState(TradingStatus.On, Map.empty)

  val _Status = Focus[TradeState](_.status)
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

final case class Prices(
    ask: Prices.Ask,
    bid: Prices.Bid,
    high: Price,
    low: Price
) derives Eq, Show

object Prices:
  def empty: Prices = Prices(Map.empty, Map.empty, Price(0.0), Price(0.0))

  type Ask = Map[AskPrice, Quantity]
  type Bid = Map[BidPrice, Quantity]

  val _Ask  = Focus[Prices](_.ask)
  val _Bid  = Focus[Prices](_.bid)
  val _High = Focus[Prices](_.high)
  val _Low  = Focus[Prices](_.low)

  given Codec[Prices] =
    val encoder = Encoder.instance[Prices] { p =>
      Json.obj(
        "ask"  -> Encoder.encodeMap[String, Quantity].apply(p.ask.map { case (k, v) => k.show -> v }),
        "bid"  -> Encoder.encodeMap[String, Quantity].apply(p.bid.map { case (k, v) => k.show -> v }),
        "high" -> Json.fromBigDecimal(p.high.value),
        "low"  -> Json.fromBigDecimal(p.low.value)
      )
    }

    val decoder = Decoder.instance { c =>
      for
        a <- c.downField("ask").as[Map[String, Quantity]]
        k = a.map { case (k, v) => Price(BigDecimal(k)) -> v }
        b <- c.downField("bid").as[Map[String, Quantity]]
        d = b.map { case (k, v) => Price(BigDecimal(k)) -> v }
        h <- c.downField("high").as[Price]
        l <- c.downField("low").as[Price]
      yield Prices(k, d, h, l)
    }

    Codec.from(decoder, encoder)

  object __AtAsk:
    def apply(p: AskPrice): Optional[Prices, Option[Quantity]] =
      _Ask.andThen(At.atMap[AskPrice, Quantity].at(p))

  object __AtBid:
    def apply(p: BidPrice): Optional[Prices, Option[Quantity]] =
      _Bid.andThen(At.atMap[BidPrice, Quantity].at(p))
