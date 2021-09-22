package trading.core

import java.time.Instant

import trading.commands.TradeCommand
import trading.domain._
import trading.state._

import cats.data.NonEmptyList
import weaver.FunSuite
import weaver.scalacheck.Checkers

object EventSourceSuite extends FunSuite with Checkers {

  val symbol: Symbol     = "EURUSD"
  val ts: Timestamp      = Instant.parse("2021-09-16T14:00:00.00Z")
  val price: Price       = 1.1987
  val quantity: Quantity = 10

  val price2: Price       = 3.5782
  val quantity2: Quantity = 20

  test("Event source state track") {
    val st0 = TradeState.empty
    val st1 = EventSource.runS(st0)(TradeCommand.Create(symbol, TradeAction.Ask, price, quantity, "test", ts))
    val ex1 = TradeState(Map(symbol -> Prices(ask = Map(price -> quantity), bid = Map.empty)))

    val st2 = EventSource.runS(st1)(TradeCommand.Update(symbol, TradeAction.Ask, price2, quantity2, "test", ts))
    val ex2 = TradeState(Map(symbol -> Prices(ask = Map(price -> quantity, price2 -> quantity2), bid = Map.empty)))

    val st3 = EventSource.runS(st2)(TradeCommand.Delete(symbol, TradeAction.Ask, price, "test", ts))
    val ex3 = TradeState(Map(symbol -> Prices(ask = Map(price2 -> quantity2), bid = Map.empty)))

    val st4 = EventSource.runS(st3)(TradeCommand.Create(symbol, TradeAction.Bid, price, quantity, "test", ts))
    val ex4 = TradeState(Map(symbol -> Prices(ask = Map(price2 -> quantity2), bid = Map(price -> quantity))))

    NonEmptyList
      .of(
        expect.same(st1, ex1),
        expect.same(st2, ex2),
        expect.same(st3, ex3),
        expect.same(st4, ex4)
      )
      .reduce
  }

}
