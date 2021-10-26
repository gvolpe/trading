package trading.domain

import cats.{ Eq, Show }
import cats.syntax.all.*
import io.circe.{ Decoder, Encoder, Json }

enum VoteResult:
  case Up, Down

object VoteResult:
  given Eq[VoteResult]   = Eq.fromUniversalEquals
  given Show[VoteResult] = Show.fromToString

  given Decoder[VoteResult] = Decoder[String].emap[VoteResult] { str =>
    Either.catchNonFatal(VoteResult.valueOf(str)).leftMap(_.getMessage)
  }

  given Encoder[VoteResult] = Encoder[String].contramap[VoteResult](_.toString)
