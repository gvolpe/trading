package trading.domain

import cats.Show
import cats.derived.semiauto.*
import io.circe.Codec

final case class Forecast(
    id: ForecastId,
    symbol: Symbol,
    tag: ForecastTag,
    description: ForecastDescription,
    score: ForecastScore
) derives Codec.AsObject, Show
