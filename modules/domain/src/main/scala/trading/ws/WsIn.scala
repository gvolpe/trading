package trading.ws

import trading.domain.*

import cats.Show
import io.circe.Codec

// TODO: Could introduce a single message, which would be an example of a Query (CQRS)
// entering the system.
enum WsIn derives Codec.AsObject:
  case Close
  case Heartbeat
  case Subscribe(symbol: Symbol)
  case Unsubscribe(symbol: Symbol)

object WsIn:
  given Show[WsIn] = Show.fromToString
