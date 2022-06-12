package trading.domain

import cats.Show
import io.circe.Codec

// FIXME: derivation does not work
//enum TradeAction derives Codec.AsObject, Show:
enum TradeAction derives Codec.AsObject:
  case Ask, Bid

object TradeAction:
  given Show[ForecastTag] = Show.fromToString
