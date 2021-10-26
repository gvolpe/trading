package trading.domain

import cats.{ Eq, Show }
import io.circe.Codec

enum ForecastTag derives Codec.AsObject:
  case Long, Short, Unknown

object ForecastTag:
  given Eq[ForecastTag]   = Eq.fromUniversalEquals
  given Show[ForecastTag] = Show.fromToString
