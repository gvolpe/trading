package trading.core

import java.time.Instant

import trading.commands.TradeCommand
import trading.domain._
import trading.state._

import weaver.FunSuite
import weaver.scalacheck.Checkers

object EventSourceSuite extends FunSuite with Checkers {

  val symbol: Symbol     = "EURUSD"
  val ts: Timestamp      = Instant.parse("2021-09-16T14:00:00.00Z")
  val price: Price       = 1.1987
  val quantity: Quantity = 10

  test("event source state track") {
    val st  = TradeState.empty
    val nst = EventSource.runS(st)(TradeCommand.Create(symbol, TradeAction.Ask, price, quantity, "test", ts))

    println(TradeState.__Prices(symbol).getOption(st))

    val expected = TradeState(Map(symbol -> Prices(ask = Map(price -> quantity), bid = Map.empty)))

    expect.same(nst, expected)
  }

}
