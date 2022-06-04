package trading.client

import trading.ws.WsOut

import cats.effect.IO
import io.circe.parser.decode as jsonDecode

import tyrian.*
import tyrian.websocket.WebSocket
import tyrian.websocket.WebSocketEvent as WSEvent

def wsSub(ws: Option[WebSocket[IO]]): Sub[IO, Msg] =
  ws.fold(Sub.emit[IO, Msg](Msg.NoOp)) {
    _.subscribe {
      case WSEvent.Receive(str) =>
        jsonDecode[WsOut](str) match
          case Right(in) => Msg.Recv(in)
          case Left(err) =>
            println(s"Fail to decode WsOut: $err")
            Msg.NoOp
      case WSEvent.Error(err) =>
        println(s"WS error: $err")
        WsMsg.Error(err).asMsg
      case WSEvent.Open =>
        println("WS socket opened")
        Msg.NoOp
      case WSEvent.Close(code, reason) =>
        println(s"WS socket closed. Code: $code, reason: $reason")
        WsMsg.Disconnected.asMsg
      case WSEvent.Heartbeat =>
        WsMsg.Heartbeat.asMsg
    }
  }
