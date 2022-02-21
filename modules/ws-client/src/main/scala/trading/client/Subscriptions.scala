package trading.client

import trading.ws.WsOut

import io.circe.parser.decode as jsonDecode

import tyrian.*
import tyrian.websocket.*

def wsSub(ws: WebSocket): Sub[Msg] =
  ws.subscribe {
    case WebSocketEvent.Receive(str) =>
      jsonDecode[WsOut](str) match
        case Right(in) => Msg.Recv(in)
        case Left(err) =>
          println(s"Fail to decode WsOut: $err")
          Msg.NoOp
    case WebSocketEvent.Error(err) =>
      println(s"WS error: $err")
      Msg.NoOp
    case WebSocketEvent.Open =>
      println("WS socket opened")
      Msg.NoOp
    case WebSocketEvent.Close =>
      println("WS socket closed")
      Msg.Recv(WsMsg.CloseConnection)
  }
