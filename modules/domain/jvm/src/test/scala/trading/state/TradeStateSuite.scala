package trading.state

import trading.domain.*
import trading.domain.arbitraries.given
import trading.domain.cogen.given

import monocle.law.discipline.*
import org.scalacheck.Arbitrary
import org.scalacheck.Test.Parameters
import weaver.FunSuite
import weaver.discipline.Discipline

object TradeStateSuite extends FunSuite with Discipline:
  import Prices.*, TradeState.*

  val symbol: Symbol     = Symbol.EURUSD
  val price: Price       = Price(1.5123)
  val askPrice: AskPrice = Price(1.6537)
  val bidPrice: BidPrice = Price(1.3908)

  val p: Parameters => Parameters = _.withMinSuccessfulTests(10)

  checkAll("__AtAsk Optional", OptionalTests(__AtAsk(askPrice)), p)
  checkAll("__AtBid Optional", OptionalTests(__AtBid(bidPrice)), p)
  checkAll("__Prices At Optional", OptionalTests(__Prices.at(symbol)), p)
  checkAll("__Prices Index Optional", OptionalTests(__Prices.index(symbol)), p)
  checkAll("__AskPrices Optional", OptionalTests(__AskPrices(symbol)), p)
  checkAll("__BidPrices Optional", OptionalTests(__BidPrices(symbol)), p)
