package trading.ws

import trading.domain.*

import cats.Show
import cats.derived.*
import io.circe.Codec

//FIXME: Derivation does not work
//enum WsIn derives Codec.AsObject, Show:
enum WsIn derives Codec.AsObject:
  case Close
  case Heartbeat
  case Subscribe(symbol: Symbol)
  case Unsubscribe(symbol: Symbol)

object WsIn:
  given Show[WsIn] = Show.fromToString
