package trading.domain

import cats.{ Eq, Show }
import cats.derived.*
import cats.syntax.all.*
import io.circe.Codec

enum ForecastTag derives Codec.AsObject, Eq, Show:
  case Long, Short, Unknown

object ForecastTag:
  def from(str: String): ForecastTag =
    Either.catchNonFatal(valueOf(str)).getOrElse(Unknown)
