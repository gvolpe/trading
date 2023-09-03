package trading.client

import trading.ws.WsOut

import io.circe.parser.decode as jsonDecode

import tyrian.websocket.WebSocketEvent as WSEvent

object Subs:
  def ws: WSEvent => Msg =
    case WSEvent.Receive(str) =>
      jsonDecode[WsOut](str) match
        case Right(in) => Msg.Recv(in)
        case Left(err) => WsMsg.Error(s"Fail to decode WsOut: $err").asMsg
    case WSEvent.Error(err) =>
      WsMsg.Error(err).asMsg
    case WSEvent.Open =>
      Msg.NoOp
    case WSEvent.Close(code, reason) =>
      WsMsg.Disconnected(code, reason).asMsg
    case WSEvent.Heartbeat =>
      WsMsg.Heartbeat.asMsg
