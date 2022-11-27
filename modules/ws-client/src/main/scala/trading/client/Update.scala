package trading.client

import trading.client.Model.*
import trading.domain.*
import trading.ws.{ WsIn, WsOut }

import cats.effect.IO
import cats.syntax.all.*

import org.scalajs.dom
import tyrian.*
import tyrian.cmds.Dom

def disconnected(model: Model): (Model, Cmd[IO, Msg]) =
  model.copy(error = "Disconnected from server, please click on Connect.".some) -> Cmd.None

def refocusInput: Cmd[IO, Msg] =
  Dom.focus("symbol-input")(_.fold(e => Msg.FocusError(ElemId(e.elementId)), _ => Msg.NoOp))

def runUpdates(model: Model): Msg => (Model, Cmd[IO, Msg]) =
  case Msg.NoOp =>
    model -> Cmd.None

  case Msg.FocusError(id) =>
    model.copy(error = s"Fail to focus on ID: ${id.show}".some) -> Cmd.None

  case Msg.ConnStatus(wsMsg) =>
    val (ws, cmd) = model.socket.update(wsMsg)
    model.copy(socket = ws, error = ws.error) -> cmd

  case Msg.CloseAlerts =>
    model.copy(error = None, sub = None, unsub = None) -> refocusInput

  case Msg.SymbolChanged(in) if in.length == 6 =>
    model.copy(symbol = Symbol(in), input = in) -> Cmd.None

  case Msg.SymbolChanged(in) =>
    model.copy(input = in) -> Cmd.None

  case Msg.Subscribe =>
    (model.socket.id, model.symbol) match
      case (_, Symbol.XEMPTY) =>
        model.copy(error = "Invalid symbol".some) -> Cmd.None
      case (Some(_), sl) =>
        val nm = model.copy(sub = sl.some, symbol = mempty, input = mempty)
        nm -> Cmd.Batch(model.socket.publish(WsIn.Subscribe(sl)), refocusInput)
      case (None, _) =>
        disconnected(model)

  case Msg.Unsubscribe(symbol) =>
    model.socket.id.fold(disconnected(model)) { _ =>
      val nm = model.copy(unsub = symbol.some, alerts = model.alerts - symbol)
      nm -> Cmd.Batch(model.socket.publish(WsIn.Unsubscribe(symbol)), refocusInput)
    }

  case Msg.Recv(WsOut.Attached(sid)) =>
    model.socket.id match
      case None =>
        _SocketId.replace(sid.some)(model) -> Cmd.None
      case Some(_) =>
        model -> Cmd.None

  case Msg.Recv(WsOut.OnlineUsers(online)) =>
    model.copy(onlineUsers = online) -> Cmd.None

  case Msg.Recv(WsOut.Notification(t: Alert.TradeAlert)) =>
    model.copy(alerts = model.alerts.updated(t.symbol, t)) -> Cmd.None

  case Msg.Recv(WsOut.Notification(t: Alert.TradeUpdate)) =>
    model.copy(tradingStatus = t.status) -> Cmd.None
