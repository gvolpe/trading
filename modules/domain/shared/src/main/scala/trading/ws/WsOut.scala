package trading.ws

import trading.domain.*

import cats.{ Eq, Show }
import cats.derived.*
import cats.syntax.eq.*
import io.circe.Codec

enum WsOut derives Codec.AsObject, Show:
  case Attached(sid: SocketId, onlineUsers: Long)
  case Notification(alert: Alert)

object WsOut:
  given Eq[WsOut] = Eq.instance {
    case (x: Attached, y: Attached)         => x == y  // universal eq
    case (Notification(x), Notification(y)) => x === y // Eq[Alert] instance
    case _                                  => false
  }
