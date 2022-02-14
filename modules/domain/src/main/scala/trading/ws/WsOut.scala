package trading.ws

import trading.domain.*

import cats.Show
import cats.derived.*
import io.circe.Codec

enum WsOut derives Codec.AsObject, Show:
  case Attached(sid: SocketId, onlineUsers: Long)
  case Notification(alert: Alert)
