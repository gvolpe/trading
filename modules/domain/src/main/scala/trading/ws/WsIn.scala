package trading.ws

import trading.domain.*

import cats.Show
import io.circe.Codec

enum WsIn derives Codec.AsObject:
  case Close
  case Heartbeat
  case Subscribe(symbol: Symbol)
  case Unsubscribe(symbol: Symbol)

object WsIn:
  given Show[WsIn] = Show.show[WsIn](_.toString)
