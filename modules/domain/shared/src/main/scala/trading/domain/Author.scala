package trading.domain

import cats.Show
import cats.derived.*
import io.circe.Codec

final case class Author(
    id: AuthorId,
    name: AuthorName,
    website: Option[Website],
    forecasts: Set[ForecastId]
) derives Codec.AsObject, Show
