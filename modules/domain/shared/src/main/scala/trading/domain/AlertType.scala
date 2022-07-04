package trading.domain

import cats.{ Eq, Show }
import cats.derived.*
import cats.syntax.all.*
import io.circe.{ Decoder, Encoder, Json }

enum AlertType derives Eq, Show:
  case StrongBuy, StrongSell, Neutral, Buy, Sell

object AlertType:
  given Decoder[AlertType] = Decoder[String].emap[AlertType] { str =>
    Either.catchNonFatal(valueOf(str)).leftMap(_.getMessage)
  }

  given Encoder[AlertType] = Encoder[String].contramap[AlertType](_.toString)
