package trading.domain

import cats.Show
import io.circe.Codec

enum TradeAction derives Codec.AsObject:
  case Ask, Bid

object TradeAction:
  given Show[TradeAction] = Show.fromToString
