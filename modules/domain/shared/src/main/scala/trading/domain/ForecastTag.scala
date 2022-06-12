package trading.domain

import cats.syntax.all.*
import cats.{ Eq, Show }
import io.circe.Codec

// FIXME: derivation does not work
// import cats.derived.*
// enum ForecastTag derives Codec.AsObject, Eq, Show:
enum ForecastTag derives Codec.AsObject:
  case Long, Short, Unknown

object ForecastTag:
  given Eq[ForecastTag]   = Eq.fromUniversalEquals
  given Show[ForecastTag] = Show.fromToString

  def from(str: String): ForecastTag =
    Either.catchNonFatal(valueOf(str)).getOrElse(Unknown)
