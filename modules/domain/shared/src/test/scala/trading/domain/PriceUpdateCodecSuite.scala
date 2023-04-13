package trading.domain

import trading.state.Prices

import io.circe.parser.decode as jsonDecode
import io.circe.syntax.*
import weaver.FunSuite

object PriceUpdateCodecSuite extends FunSuite:

  test("codec roundtrip for PriceUpdate") {
    val p   = PriceUpdate(Symbol.EURUSD, Prices(Map.empty, Map(Price(2.3) -> Quantity(45)), Price(2.7), Price(7.5)))
    val enc = p.asJson.noSpaces
    val dec = jsonDecode[PriceUpdate](enc)
    expect.same(Right(p), dec)
  }
