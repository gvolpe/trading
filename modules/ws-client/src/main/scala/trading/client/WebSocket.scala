package trading.client

import cats.effect.kernel.{ Async, Sync }
import cats.syntax.all.*
import fs2.Stream
import org.scalajs.dom
import tyrian.Cmd
import tyrian.Sub
import tyrian.websocket.WebSocketEvent
import util.Functions

import scala.concurrent.duration.*
import scala.collection.mutable.Queue

/* Temporary replacement of Tyrian's WebSocket implementation with a hacky bug-fix.
 * See: https://github.com/PurpleKingdomGames/tyrian/issues/149 */

/** Helper WebSocket instance to store in your model that acts as a controller */
final class WebSocket[F[_]: Async](liveSocket: LiveSocket[F]):
  /** Disconnect from this WebSocket */
  def disconnect[Msg]: Cmd[F, Msg] =
    Cmd.SideEffect(liveSocket.socket.close(1000, "Graceful shutdown"))

  /** Publish a message to this WebSocket */
  def publish[Msg](message: String): Cmd[F, Msg] =
    Cmd.SideEffect(liveSocket.socket.send(message))

  /** Subscribe to messages from this WebSocket */
  def subscribe[Msg](f: WebSocketEvent => Msg): Sub[F, Msg] =
    if WebSocketReadyState.fromInt(liveSocket.socket.readyState).isOpen then liveSocket.subs.map(f)
    else Sub.emit(f(WebSocketEvent.Error("Connection not ready")))

/** The running instance of the WebSocket */
final class LiveSocket[F[_]: Async](val socket: dom.WebSocket, val subs: Sub[F, WebSocketEvent])

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

final case class KeepAliveSettings(timeout: FiniteDuration)
object KeepAliveSettings:
  def default = KeepAliveSettings(20.seconds)

object WebSocket:
  /** Acquires a WebSocket connection with default keep-alive message */
  def connect[F[_]: Async, Msg](address: String)(resultToMessage: WebSocketConnect[F] => Msg): Cmd[F, Msg] =
    Cmd.Run(connectTask(address), resultToMessage)

  /** Acquires a WebSocket connection with default keep-alive message and a custom message onOpen */
  def connect[F[_]: Async, Msg](address: String, onOpenMessage: String)(
      resultToMessage: WebSocketConnect[F] => Msg
  ): Cmd[F, Msg] =
    Cmd.Run(connectTask(address, onOpenMessage), resultToMessage)

  /** Acquires a WebSocket connection with custom keep-alive message */
  def connect[F[_]: Async, Msg](address: String, keepAliveSettings: KeepAliveSettings)(
      resultToMessage: WebSocketConnect[F] => Msg
  ): Cmd[F, Msg] =
    Cmd.Run(connectTask(address, keepAliveSettings), resultToMessage)

  /** Acquires a WebSocket connection with a custom keep-alive message and a custom message onOpen */
  def connect[F[_]: Async, Msg](address: String, onOpenMessage: String, keepAliveSettings: KeepAliveSettings)(
      resultToMessage: WebSocketConnect[F] => Msg
  ): Cmd[F, Msg] =
    Cmd.Run(connectTask(address, onOpenMessage, keepAliveSettings), resultToMessage)

  private def connectTask[F[_]: Async](address: String): F[WebSocketConnect[F]] =
    Async[F].map(newConnection(address, None, KeepAliveSettings.default))(ls => WebSocketConnect.Socket(WebSocket(ls)))

  private def connectTask[F[_]: Async](address: String, onOpenMessage: String): F[WebSocketConnect[F]] =
    Async[F].map(newConnection(address, Option(onOpenMessage), KeepAliveSettings.default))(ls =>
      WebSocketConnect.Socket(WebSocket(ls))
    )

  private def connectTask[F[_]: Async](address: String, keepAliveSettings: KeepAliveSettings): F[WebSocketConnect[F]] =
    Async[F].map(newConnection(address, None, keepAliveSettings))(ls => WebSocketConnect.Socket(WebSocket(ls)))

  private def connectTask[F[_]: Async](
      address: String,
      onOpenMessage: String,
      keepAliveSettings: KeepAliveSettings
  ): F[WebSocketConnect[F]] =
    Async[F].map(newConnection(address, Some(onOpenMessage), keepAliveSettings))(ls =>
      WebSocketConnect.Socket(WebSocket(ls))
    )

  private def newConnection[F[_]: Async](
      address: String,
      onOpenSendMessage: Option[String],
      settings: KeepAliveSettings
  ): F[LiveSocket[F]] =
    Async[F].delay {
      val socket    = new dom.WebSocket(address)
      val keepAlive = new KeepAlive(socket, settings)
      val q         = Queue.empty[WebSocketEvent]

      val msgListener = Functions.fun { e =>
        val event = WebSocketEvent.Receive(e.asInstanceOf[dom.MessageEvent].data.toString)
        q.enqueue(event)
      }

      val errListener = Functions.fun { e =>
        val msg =
          try e.asInstanceOf[dom.ErrorEvent].message
          catch { case _: Throwable => "Unknown" }
        q.enqueue(WebSocketEvent.Error(msg))
      }

      val closeListener = Functions.fun { e =>
        val ev = e.asInstanceOf[dom.CloseEvent]
        q.enqueue(WebSocketEvent.Close(ev.code, ev.reason))
      }

      val openListener = Functions.fun { _ =>
        onOpenSendMessage.foreach(msg => socket.send(msg))
        q.enqueue(WebSocketEvent.Open)
      }

      socket.addEventListener("message", msgListener)
      socket.addEventListener("error", errListener)
      socket.addEventListener("open", openListener)
      socket.addEventListener("close", closeListener)

      val subs =
        Sub.Batch(
          Sub.make(
            "ws-queue",
            Stream.repeatEval {
              Sync[F].delay {
                try Some(q.dequeue())
                catch _ => None
              }
            }.unNone
          ),
          // TODO: not really sure this clean up actually works, but app seems to work fine...
          Sub.make[F, WebSocketEvent, Unit]("ws-listeners-cleanup")(_ => Sync[F].unit)(_ =>
            Sync[F].delay {
              socket.removeEventListener("message", msgListener)
              socket.removeEventListener("error", errListener)
              socket.removeEventListener("open", openListener)
              socket.removeEventListener("close", closeListener)
            }
          ),
          keepAlive.run
        )

      LiveSocket(socket, subs)
    }

  final class KeepAlive[F[_]: Async](socket: dom.WebSocket, settings: KeepAliveSettings):
    @SuppressWarnings(Array("scalafix:DisableSyntax.null"))
    def run: Sub[F, WebSocketEvent] =
      if socket != null && WebSocketReadyState.fromInt(socket.readyState).isOpen then
        Sub.every(settings.timeout, "ws-heartbeat").map(_ => WebSocketEvent.Heartbeat)
      else Sub.None

sealed trait WebSocketConnect[F[_]: Async]
object WebSocketConnect:
  final case class Error[F[_]: Async](msg: String)               extends WebSocketConnect[F]
  final case class Socket[F[_]: Async](webSocket: WebSocket[F]) extends WebSocketConnect[F]
