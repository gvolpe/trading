package trading.client

import org.scalajs.dom
import tyrian.{ Cmd, Sub, Task }
import tyrian.websocket.WebSocketEvent
import util.Functions

// Adapted from tyrian.websocket.WebSocket
final class WS(liveSocket: LiveSocket):
  def publish[Msg](message: String): Cmd[Msg] =
    Cmd.SideEffect(() => liveSocket.socket.send(message))

  def subscribe[Msg](f: WebSocketEvent => Msg): Sub[Msg] =
    if WebSocketReadyState.fromInt(liveSocket.socket.readyState).isOpen then liveSocket.subs.map(f)
    else Sub.emit(f(WebSocketEvent.Close))

final class LiveSocket(val socket: dom.WebSocket, val subs: Sub[WebSocketEvent])

enum WebSocketReadyState derives CanEqual:
  case CONNECTING, OPEN, CLOSING, CLOSED

  def isOpen: Boolean =
    this match
      case CLOSED  => false
      case CLOSING => false
      case _       => true

object WebSocketReadyState:
  def fromInt(i: Int): WebSocketReadyState =
    i match {
      case 0 => CONNECTING
      case 1 => OPEN
      case 2 => CLOSING
      case 3 => CLOSED
      case _ => CLOSED
    }

object WS:
  def connect(address: String): Either[String, WS] =
    newConnection(address, None).map(WS(_))

  def connect(address: String, onOpenMessage: String): Either[String, WS] =
    newConnection(address, Option(onOpenMessage)).map(WS(_))

  private def newConnection(
      address: String,
      onOpenSendMessage: Option[String],
      withKeepAliveMessage: Option[String] = None
  ): Either[String, LiveSocket] =
    try {
      val socket    = new dom.WebSocket(address)
      val keepAlive = new KeepAlive(socket, withKeepAliveMessage)

      val subs =
        Sub.Batch(
          Sub.fromEvent("message", socket) { e =>
            Some(WebSocketEvent.Receive(e.asInstanceOf[dom.MessageEvent].data.toString))
          },
          Sub.fromEvent("error", socket) { _ =>
            Some(WebSocketEvent.Error("Web socket connection error"))
          },
          Sub.fromEvent("close", socket) { e =>
            keepAlive.cancel()
            Some(WebSocketEvent.Close)
          },
          Sub.fromEvent("open", socket) { e =>
            onOpenSendMessage.foreach(msg => socket.send(msg))
            keepAlive.run()
            Some(WebSocketEvent.Open)
          }
        )

      Right(LiveSocket(socket, subs))
    } catch {
      case e: Throwable =>
        Left(s"Error trying to set up websocket: ${e.getMessage}")
    }

  final class KeepAlive(socket: dom.WebSocket, msg: Option[String]):
    private var timerId = 0;

    def run(): Unit =
      if socket != null && WebSocketReadyState.fromInt(socket.readyState).isOpen then
        println("[info] Sending heartbeat 💓")
        socket.send(msg.getOrElse("{ \"Heartbeat\": {} }"))
      timerId = dom.window.setTimeout(Functions.fun0(() => run()), 20000)

    def cancel(): Unit =
      if (timerId <= 0) then dom.window.clearTimeout(timerId) else ()
