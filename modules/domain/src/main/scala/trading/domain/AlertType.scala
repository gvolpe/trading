package trading.domain

import io.circe.Codec

enum AlertType derives Codec.AsObject:
  case StrongBuy, StrongSell, Neutral, Buy, Sell
