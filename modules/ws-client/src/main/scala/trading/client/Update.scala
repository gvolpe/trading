package trading.client

import trading.domain.{ Alert, Symbol }
import trading.ws.{ WsIn, WsOut }

import cats.syntax.eq.*
import io.circe.syntax.*

import org.scalajs.dom
import tyrian.*

def disconnected(model: Model): (Model, Cmd[Msg]) =
  model.copy(error = Some("Disconnected from server, please click on Connect.")) -> Cmd.Empty

def refocusInput: Cmd[Msg] = Cmd.SideEffect { () =>
  val elem = dom.document.querySelector("#symbol-input").asInstanceOf[dom.raw.HTMLInputElement]
  elem.focus()
}

def runUpdates(msg: Msg, model: Model): (Model, Cmd[Msg]) =
  msg match
    case Msg.NoOp =>
      model -> Cmd.Empty

    case Msg.Connect =>
      WS.connect(model.wsUrl) match
        case Left(cause) =>
          model -> Cmd.Emit(Msg.ConnStatus(WsMsg.Error(cause)))
        case Right(ws) =>
          model.copy(error = None, ws = Some(ws)) -> refocusInput

    case Msg.CloseAlerts =>
      model.copy(error = None, sub = None, unsub = None) -> refocusInput

    case Msg.SymbolChanged(in) if in.length == 6 =>
      model.copy(symbol = Symbol.unsafeFrom(in), input = in) -> Cmd.Empty

    case Msg.SymbolChanged(in) =>
      model.copy(input = in) -> Cmd.Empty

    case Msg.Subscribe =>
      if model.symbol === Symbol.XXXXXX then model.copy(error = Some("Invalid symbol")) -> Cmd.Empty
      else
        model.socketId match
          case Some(_) =>
            val nm       = model.copy(sub = Some(model.symbol), symbol = Symbol.XXXXXX, input = "")
            val in: WsIn = WsIn.Subscribe(model.symbol)
            val cmd      = model.ws.map(ws => Cmd.Batch(ws.publish(in.asJson.noSpaces), refocusInput))
            nm -> cmd.getOrElse(Cmd.Empty)
          case None =>
            disconnected(model)

    case Msg.Unsubscribe(symbol) =>
      model.socketId match
        case Some(_) =>
          val nm       = model.copy(unsub = Some(symbol), alerts = model.alerts - symbol)
          val in: WsIn = WsIn.Unsubscribe(symbol)
          val cmd      = model.ws.map(ws => Cmd.Batch(ws.publish(in.asJson.noSpaces), refocusInput))
          nm -> cmd.getOrElse(Cmd.Empty)
        case None =>
          disconnected(model)

    case Msg.Recv(WsOut.Attached(sid, users)) =>
      model.copy(socketId = Some(sid), onlineUsers = users.toInt) -> Cmd.Empty

    case Msg.Recv(WsOut.Notification(t: Alert.TradeAlert)) =>
      model.copy(alerts = model.alerts.updated(t.symbol, t)) -> Cmd.Empty

    case Msg.Recv(WsOut.Notification(t: Alert.TradeUpdate)) =>
      model.copy(tradingStatus = t.status) -> Cmd.Empty

    case Msg.ConnStatus(WsMsg.Disconnected) =>
      model.copy(socketId = None) -> Cmd.Empty

    case Msg.ConnStatus(WsMsg.Error(cause)) =>
      model.copy(error = Some(s"Connection error: $cause")) -> Cmd.Empty
