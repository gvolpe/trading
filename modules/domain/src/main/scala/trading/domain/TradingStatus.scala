package trading.domain

import cats.syntax.all.*
import cats.{ Eq, Show }
import io.circe.{ Decoder, Encoder, Json }

enum TradingStatus:
  case On, Off

object TradingStatus:
  def from(str: String): Option[TradingStatus] =
    Either.catchNonFatal(valueOf(str)).toOption

  given Eq[TradingStatus]   = Eq.fromUniversalEquals
  given Show[TradingStatus] = Show.fromToString

  given Decoder[TradingStatus] = Decoder[String].emap[TradingStatus] { str =>
    Either.catchNonFatal(valueOf(str)).leftMap(_.getMessage)
  }

  given Encoder[TradingStatus] = Encoder[String].contramap[TradingStatus](_.toString)
