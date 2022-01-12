package trading.domain

import cats.Show
// FIXME: importing * does not work
import cats.derived.semiauto.{ derived, product }
import io.circe.Codec

enum TradeAction derives Codec.AsObject, Show:
  case Ask, Bid
