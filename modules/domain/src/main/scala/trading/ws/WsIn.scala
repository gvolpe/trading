package trading.ws

import trading.domain.*

import cats.Show
// FIXME: importing * does not work
import cats.derived.semiauto.{ derived, product }
import io.circe.Codec

enum WsIn derives Codec.AsObject, Show:
  case Close
  case Heartbeat
  case Subscribe(symbol: Symbol)
  case Unsubscribe(symbol: Symbol)
