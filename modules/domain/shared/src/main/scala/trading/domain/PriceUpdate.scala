package trading.domain

import trading.state.Prices

import cats.{ Eq, Show }
// FIXME: importing * does not work
import cats.derived.semiauto.{ derived, product, productOrder }
import io.circe.Codec

final case class PriceUpdate(
    symbol: Symbol,
    prices: Prices
) derives Codec.AsObject, Eq, Show
