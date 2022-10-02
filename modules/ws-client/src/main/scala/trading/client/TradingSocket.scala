package trading.client

import trading.domain.SocketId
import trading.ws.WsIn

import cats.effect.IO
import cats.syntax.all.*
import io.circe.Encoder
import io.circe.syntax.*

import tyrian.{ Cmd, Sub }
//import tyrian.websocket.*
import tyrian.websocket.WebSocketEvent as WSEvent

final case class TradingSocket(
    wsUrl: WsUrl,
    id: Option[SocketId],
    ws: Option[WebSocket[IO]],
    error: Option[String]
):
  val update: WsMsg => (TradingSocket, Cmd[IO, Msg]) =
    case WsMsg.Connecting =>
      this -> WebSocket.connect[IO, Msg](wsUrl, KeepAliveSettings.default) {
        case WebSocketConnect.Socket(s) => WsMsg.Connected(s).asMsg
        case WebSocketConnect.Error(e)  => WsMsg.Error(e).asMsg
      }

    case WsMsg.Connected(cws) =>
      this.copy(ws = cws.some, error = None) -> refocusInput

    case WsMsg.Disconnected(code, reason) =>
      val err = s"WS socket closed. Code: $code, reason: $reason"
      this.copy(id = None, ws = None, error = err.some) -> Cmd.None

    case WsMsg.Error(cause) =>
      this.copy(error = s"Connection error: $cause".some) -> Cmd.None

    case WsMsg.Heartbeat =>
      this -> publish[WsIn](WsIn.Heartbeat)

  def publish[A: Encoder](a: A): Cmd[IO, Msg] =
    ws.fold(Cmd.None)(_.publish(a.asJson.noSpaces))

  def subscribe(f: WSEvent => Msg): Sub[IO, Msg] =
    ws.fold(Sub.emit[IO, Msg](Msg.NoOp))(_.subscribe(f))

object TradingSocket:
  def init: TradingSocket =
    TradingSocket(WsUrl("ws://localhost:9000/v1/ws"), None, None, None)
