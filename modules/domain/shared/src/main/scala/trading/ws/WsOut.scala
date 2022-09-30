package trading.ws

import trading.domain.*

import cats.{ Eq, Show }
import cats.derived.*
import cats.syntax.eq.*
import io.circe.Codec

enum WsOut derives Codec.AsObject, Show:
  case Attached(sid: SocketId)
  case Notification(alert: Alert)
  case OnlineUsers(n: Int)

object WsOut:
  given Eq[WsOut] = Eq.instance {
    case (x: Attached, y: Attached)         => x == y  // universal eq
    case (OnlineUsers(x), OnlineUsers(y))   => x === y // Eq[Int] instance
    case (Notification(x), Notification(y)) => x === y // Eq[Alert] instance
    case _                                  => false
  }

extension (alert: Alert) def wsOut: WsOut = WsOut.Notification(alert)
