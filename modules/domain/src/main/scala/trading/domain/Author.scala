package trading.domain

import cats.Show
import cats.derived.semiauto.*
import io.circe.Codec

final case class Author(
    id: AuthorId,
    name: AuthorName,
    website: Option[Website],
    reputation: Reputation,
    forecasts: List[Int] // maybe link to published forecasts?
) derives Codec.AsObject, Show
