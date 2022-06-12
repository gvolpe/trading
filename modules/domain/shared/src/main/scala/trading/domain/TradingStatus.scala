package trading.domain

import cats.{ Eq, Show }
import cats.derived.*
import cats.syntax.all.*
import io.circe.{ Decoder, Encoder, Json }

// FIXME: derivation does not work
//enum TradingStatus derives Eq, Show:
enum TradingStatus:
  case On, Off

object TradingStatus:
  given Eq[TradingStatus]   = Eq.fromUniversalEquals
  given Show[TradingStatus] = Show.fromToString

  def from(str: String): Option[TradingStatus] =
    Either.catchNonFatal(valueOf(str)).toOption

  given Decoder[TradingStatus] = Decoder[String].emap[TradingStatus] { str =>
    Either.catchNonFatal(valueOf(str)).leftMap(_.getMessage)
  }

  given Encoder[TradingStatus] = Encoder[String].contramap[TradingStatus](_.toString)
