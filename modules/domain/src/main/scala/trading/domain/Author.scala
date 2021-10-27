package trading.domain

import cats.Show
import cats.derived.semiauto.*
import io.circe.Codec

final case class Author(
    id: AuthorId,
    name: AuthorName,
    website: Option[Website],
    //reputation: Reputation, // TODO: can be calculated on the fly?
    forecasts: List[ForecastId]
) derives Codec.AsObject, Show
