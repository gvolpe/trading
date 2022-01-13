package trading.ws

import trading.domain.*

import cats.Show
import cats.derived.semiauto.{ given, * }
import io.circe.Codec

enum WsIn derives Codec.AsObject, Show:
  case Close
  case Heartbeat
  case Subscribe(symbol: Symbol)
  case Unsubscribe(symbol: Symbol)
