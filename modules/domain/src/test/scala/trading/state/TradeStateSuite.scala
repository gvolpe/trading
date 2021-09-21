package trading.state

import trading.domain._
import trading.domain.cogen._
import trading.domain.generators._

import monocle.law.discipline._
import org.scalacheck.Arbitrary
import weaver.FunSuite
import weaver.discipline.Discipline

object TradeStateSuite extends FunSuite with Discipline {
  import Prices._, TradeState._

  val symbol: Symbol     = "EURUSD"
  val price: Price       = 1.5123
  val askPrice: AskPrice = 1.6537
  val bidPrice: BidPrice = 1.3908

  implicit val arbPrices: Arbitrary[Prices]         = Arbitrary(pricesGen)
  implicit val arbTradeState: Arbitrary[TradeState] = Arbitrary(tradeStateGen)

  checkAll("__AtAsk Optional", OptionalTests(__AtAsk(askPrice)))
  checkAll("__AtBid Optional", OptionalTests(__AtBid(bidPrice)))
  checkAll("__Prices Optional", OptionalTests(__Prices(symbol)))
  checkAll("__AskPrices Optional", OptionalTests(__AskPrices(symbol)))
  checkAll("__AskQuantity Optional", OptionalTests(__AskQuantity(symbol, price)))
  checkAll("__BidPrices Optional", OptionalTests(__BidPrices(symbol)))
  checkAll("__BidQuantity Optional", OptionalTests(__BidQuantity(symbol, price)))
}
