package trading.domain

import cats.Show
import cats.derived.*
import io.circe.Codec

enum TradeAction derives Codec.AsObject, Show:
  case Ask, Bid
