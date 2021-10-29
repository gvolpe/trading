package trading.domain

import cats.{ Eq, Show }
import cats.syntax.all.*

enum TradingStatus:
  case On, Off

object TradingStatus:
  def from(str: String): Option[TradingStatus] =
    Either.catchNonFatal(valueOf(str)).toOption

  given Eq[TradingStatus]   = Eq.fromUniversalEquals
  given Show[TradingStatus] = Show.fromToString
