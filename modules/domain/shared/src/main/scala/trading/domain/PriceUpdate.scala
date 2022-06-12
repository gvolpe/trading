package trading.domain

import trading.state.Prices

import cats.{ Eq, Show }
import cats.derived.*
import io.circe.Codec

final case class PriceUpdate(
    symbol: Symbol,
    prices: Prices
) derives Codec.AsObject, Eq, Show
