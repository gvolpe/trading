package trading.client

import trading.domain.{ Alert, Symbol }
import trading.ws.{ WsIn, WsOut }

import cats.effect.IO
import cats.syntax.eq.*
import io.circe.syntax.*

import org.scalajs.dom
import tyrian.*
import tyrian.cmds.Dom
import tyrian.websocket.{ KeepAliveSettings, WebSocket, WebSocketConnect }

def disconnected(model: Model): (Model, Cmd[IO, Msg]) =
  model.copy(error = Some("Disconnected from server, please click on Connect.")) -> Cmd.None

def refocusInput: Cmd[IO, Msg] =
  Dom.focus("symbol-input")(_.fold(e => Msg.FocusError(e.elementId), _ => Msg.NoOp))

def runUpdates(msg: Msg, model: Model): (Model, Cmd[IO, Msg]) =
  msg match
    case Msg.NoOp =>
      model -> Cmd.None

    case Msg.FocusError(id) =>
      model.copy(error = Some(s"Fail to focus on ID: $id")) -> Cmd.None

    case Msg.ConnStatus(WsMsg.Connecting) =>
      model -> WebSocket.connect[IO, Msg](model.wsUrl, KeepAliveSettings.default) {
        case WebSocketConnect.Socket(s) => WsMsg.Connected(s).asMsg
        case WebSocketConnect.Error(e)  => WsMsg.Error(e).asMsg
      }

    case Msg.ConnStatus(WsMsg.Connected(ws)) =>
      model.copy(error = None, ws = Some(ws)) -> refocusInput

    case Msg.ConnStatus(WsMsg.Disconnected) =>
      model.copy(socketId = None) -> Cmd.None

    case Msg.ConnStatus(WsMsg.Error(cause)) =>
      model.copy(error = Some(s"Connection error: $cause")) -> Cmd.None

    case Msg.ConnStatus(WsMsg.Heartbeat) =>
      model -> model.ws.map(_.publish("{ \"Heartbeat\": {} }")).getOrElse(Cmd.None)

    case Msg.CloseAlerts =>
      model.copy(error = None, sub = None, unsub = None) -> refocusInput

    case Msg.SymbolChanged(in) if in.length == 6 =>
      model.copy(symbol = Symbol.unsafeFrom(in), input = in) -> Cmd.None

    case Msg.SymbolChanged(in) =>
      model.copy(input = in) -> Cmd.None

    case Msg.Subscribe =>
      (model.socketId, model.symbol) match
        case (_, Symbol.XXXXXX) =>
          model.copy(error = Some("Invalid symbol")) -> Cmd.None
        case (Some(_), _) =>
          val nm       = model.copy(sub = Some(model.symbol), symbol = Symbol.XXXXXX, input = "")
          val in: WsIn = WsIn.Subscribe(model.symbol)
          val cmd      = model.ws.map(ws => Cmd.Batch(ws.publish(in.asJson.noSpaces), refocusInput))
          nm -> cmd.getOrElse(Cmd.None)
        case (None, _) =>
          disconnected(model)

    case Msg.Unsubscribe(symbol) =>
      model.socketId match
        case Some(_) =>
          val nm       = model.copy(unsub = Some(symbol), alerts = model.alerts - symbol)
          val in: WsIn = WsIn.Unsubscribe(symbol)
          val cmd      = model.ws.map(ws => Cmd.Batch(ws.publish(in.asJson.noSpaces), refocusInput))
          nm -> cmd.getOrElse(Cmd.None)
        case None =>
          disconnected(model)

    case Msg.Recv(WsOut.Attached(sid, users)) =>
      model.copy(socketId = Some(sid), onlineUsers = users.toInt) -> Cmd.None

    case Msg.Recv(WsOut.Notification(t: Alert.TradeAlert)) =>
      model.copy(alerts = model.alerts.updated(t.symbol, t)) -> Cmd.None

    case Msg.Recv(WsOut.Notification(t: Alert.TradeUpdate)) =>
      model.copy(tradingStatus = t.status) -> Cmd.None
