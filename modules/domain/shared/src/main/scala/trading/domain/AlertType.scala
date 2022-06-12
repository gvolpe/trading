package trading.domain

import cats.{ Eq, Show }
import cats.syntax.all.*
import io.circe.{ Decoder, Encoder, Json }

//FIXME: Derivation does not work
//import cats.derived.*
//enum AlertType derives Show:
enum AlertType:
  case StrongBuy, StrongSell, Neutral, Buy, Sell

object AlertType:
  given Eq[AlertType] = Eq.fromUniversalEquals

  given Show[AlertType] = Show.fromToString

  given Decoder[AlertType] = Decoder[String].emap[AlertType] { str =>
    Either.catchNonFatal(valueOf(str)).leftMap(_.getMessage)
  }

  given Encoder[AlertType] = Encoder[String].contramap[AlertType](_.toString)
