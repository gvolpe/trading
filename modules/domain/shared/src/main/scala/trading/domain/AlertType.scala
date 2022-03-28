package trading.domain

import cats.{ Eq, Show }
// FIXME: importing * does not work
import cats.derived.semiauto.{ derived, product }
import cats.syntax.all.*
import io.circe.{ Decoder, Encoder, Json }

enum AlertType derives Show:
  case StrongBuy, StrongSell, Neutral, Buy, Sell

object AlertType:
  given Eq[AlertType] = Eq.fromUniversalEquals

  given Decoder[AlertType] = Decoder[String].emap[AlertType] { str =>
    Either.catchNonFatal(valueOf(str)).leftMap(_.getMessage)
  }

  given Encoder[AlertType] = Encoder[String].contramap[AlertType](_.toString)
