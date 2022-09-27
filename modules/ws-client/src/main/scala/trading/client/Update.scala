package trading.client

import trading.domain.*
import trading.ws.{ WsIn, WsOut }

import cats.effect.IO
import cats.syntax.all.*
import io.circe.syntax.*

import org.scalajs.dom
import tyrian.*
import tyrian.cmds.Dom
import tyrian.websocket.{ KeepAliveSettings, WebSocket, WebSocketConnect }

def disconnected(model: Model): (Model, Cmd[IO, Msg]) =
  model.copy(error = Some("Disconnected from server, please click on Connect.")) -> Cmd.None

def refocusInput: Cmd[IO, Msg] =
  Dom.focus("symbol-input")(_.fold(e => Msg.FocusError(ElemId(e.elementId)), _ => Msg.NoOp))

def runUpdates(msg: Msg, model: Model): (Model, Cmd[IO, Msg]) =
  msg match
    case Msg.NoOp =>
      model -> Cmd.None

    case Msg.FocusError(id) =>
      model.copy(error = s"Fail to focus on ID: ${id.show}".some) -> Cmd.None

    case Msg.ConnStatus(WsMsg.Connecting) =>
      model -> WebSocket.connect[IO, Msg](model.wsUrl.value, KeepAliveSettings.default) {
        case WebSocketConnect.Socket(s) => WsMsg.Connected(s).asMsg
        case WebSocketConnect.Error(e)  => WsMsg.Error(e).asMsg
      }

    case Msg.ConnStatus(WsMsg.Connected(_ws)) =>
      model.copy(error = None, ws = _ws.some) -> refocusInput

    case Msg.ConnStatus(WsMsg.Disconnected) =>
      model.copy(socketId = None) -> Cmd.None

    case Msg.ConnStatus(WsMsg.Error(cause)) =>
      model.copy(error = s"Connection error: $cause".some) -> Cmd.None

    case Msg.ConnStatus(WsMsg.Heartbeat) =>
      model -> model.ws.map(_.publish("{ \"Heartbeat\": {} }")).getOrElse(Cmd.None)

    case Msg.CloseAlerts =>
      model.copy(error = None, sub = None, unsub = None) -> refocusInput

    case Msg.SymbolChanged(in) if in.length == 6 =>
      model.copy(symbol = Symbol.unsafeFrom(in.value), input = in) -> Cmd.None

    case Msg.SymbolChanged(in) =>
      model.copy(input = in) -> Cmd.None

    case Msg.Subscribe =>
      (model.socketId, model.symbol) match
        case (_, Symbol.XEMPTY) =>
          model.copy(error = "Invalid symbol".some) -> Cmd.None
        case (Some(_), _) =>
          val nm       = model.copy(sub = model.symbol.some, symbol = mempty, input = mempty)
          val in: WsIn = WsIn.Subscribe(model.symbol)
          val cmd      = model.ws.map(ws => Cmd.Batch(ws.publish(in.asJson.noSpaces), refocusInput))
          nm -> cmd.getOrElse(Cmd.None)
        case (None, _) =>
          disconnected(model)

    case Msg.Unsubscribe(symbol) =>
      model.socketId match
        case Some(_) =>
          val nm       = model.copy(unsub = symbol.some, alerts = model.alerts - symbol)
          val in: WsIn = WsIn.Unsubscribe(symbol)
          val cmd      = model.ws.map(ws => Cmd.Batch(ws.publish(in.asJson.noSpaces), refocusInput))
          nm -> cmd.getOrElse(Cmd.None)
        case None =>
          disconnected(model)

    case Msg.Recv(WsOut.Attached(sid, users)) =>
      model.copy(socketId = sid.some, onlineUsers = users) -> Cmd.None

    case Msg.Recv(WsOut.Notification(t: Alert.TradeAlert)) =>
      model.copy(alerts = model.alerts.updated(t.symbol, t)) -> Cmd.None

    case Msg.Recv(WsOut.Notification(t: Alert.TradeUpdate)) =>
      model.copy(tradingStatus = t.status) -> Cmd.None
