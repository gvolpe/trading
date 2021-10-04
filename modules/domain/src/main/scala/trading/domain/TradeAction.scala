package trading.domain

import io.circe.Codec

enum TradeAction derives Codec.AsObject:
  case Ask, Bid
