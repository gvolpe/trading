package trading.domain

import cats.{ Eq, Show }
// FIXME: importing * does not work
import cats.derived.semiauto.{ derived, product, productOrder }
import cats.syntax.all.*
import io.circe.{ Decoder, Encoder, Json }

enum VoteResult derives Eq, Show:
  def asInt: Int = this match
    case Up   => 1
    case Down => -1

  case Up, Down

object VoteResult:
  given Decoder[VoteResult] = Decoder[String].emap[VoteResult] { str =>
    Either.catchNonFatal(valueOf(str)).leftMap(_.getMessage)
  }

  given Encoder[VoteResult] = Encoder[String].contramap[VoteResult](_.toString)
