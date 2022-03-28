package trading.domain

import cats.syntax.all.*
import cats.{ Eq, Show }
// FIXME: importing * does not work
import cats.derived.semiauto.{ derived, product, productOrder }
import io.circe.Codec

enum ForecastTag derives Codec.AsObject, Eq, Show:
  case Long, Short, Unknown

object ForecastTag:
  def from(str: String): ForecastTag =
    Either.catchNonFatal(valueOf(str)).getOrElse(Unknown)
