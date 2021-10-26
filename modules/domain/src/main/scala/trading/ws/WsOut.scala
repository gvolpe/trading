package trading.ws

import trading.domain.*

import cats.Show
import io.circe.Codec

enum WsOut derives Codec.AsObject:
  case Attached(sid: SocketId, onlineUsers: Long)
  case Notification(alert: Alert)

object WsOut:
  given Show[WsOut] = Show.fromToString
