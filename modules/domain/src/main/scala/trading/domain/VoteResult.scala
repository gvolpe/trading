package trading.domain

import cats.{ Eq, Show }
import cats.syntax.all.*
import io.circe.{ Decoder, Encoder, Json }

enum VoteResult:
  def asInt: Int = this match
    case Up   => 1
    case Down => -1

  case Up, Down

object VoteResult:
  given Eq[VoteResult]   = Eq.fromUniversalEquals
  given Show[VoteResult] = Show.fromToString

  given Decoder[VoteResult] = Decoder[String].emap[VoteResult] { str =>
    Either.catchNonFatal(VoteResult.valueOf(str)).leftMap(_.getMessage)
  }

  given Encoder[VoteResult] = Encoder[String].contramap[VoteResult](_.toString)
