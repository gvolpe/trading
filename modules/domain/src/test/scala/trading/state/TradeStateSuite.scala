package trading.state

import trading.domain.*
import trading.domain.arbitraries.given
import trading.domain.cogen.given

import monocle.law.discipline.*
import org.scalacheck.Arbitrary
import weaver.FunSuite
import weaver.discipline.Discipline

object TradeStateSuite extends FunSuite with Discipline:
  import Prices.*, TradeState.*

  val symbol: Symbol     = Symbol.EURUSD
  val price: Price       = Price(1.5123)
  val askPrice: AskPrice = Price(1.6537)
  val bidPrice: BidPrice = Price(1.3908)

  checkAll("__AtAsk Optional", OptionalTests(__AtAsk(askPrice)))
  checkAll("__AtBid Optional", OptionalTests(__AtBid(bidPrice)))
  checkAll("__Prices At Optional", OptionalTests(__Prices.at(symbol)))
  checkAll("__Prices Index Optional", OptionalTests(__Prices.index(symbol)))
  checkAll("__AskPrices Optional", OptionalTests(__AskPrices(symbol)))
  checkAll("__BidPrices Optional", OptionalTests(__BidPrices(symbol)))
