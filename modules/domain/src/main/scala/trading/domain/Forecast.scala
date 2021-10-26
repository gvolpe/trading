package trading.domain

import cats.Show
import cats.derived.semiauto.*
import io.circe.Codec

final case class Forecast(
    id: ForecastId,
    authorId: AuthorId,
    symbol: Symbol,
    tag: ForecastTag,
    description: String,
    score: ForecastScore
) derives Codec.AsObject, Show
