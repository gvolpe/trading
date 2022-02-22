package trading.client

import trading.domain.{ Alert, Symbol }
import trading.ws.{ WsIn, WsOut }

import io.circe.syntax.*

import tyrian.*

def disconnected(model: Model): (Model, Cmd[Msg]) =
  model.copy(error = Some("Disconnected from server, please click on Connect.")) -> Cmd.Empty

// TODO: implement
def refocusInput: Cmd[Msg] = Cmd.SideEffect { () =>
  ()
  //org.scalajs.dom.document.focus()
//Task.attempt (\_ -> NoOp) (Dom.focus "symbol-input")
}

def runUpdates(msg: Msg, model: Model): (Model, Cmd[Msg]) =
  msg match
    case Msg.NoOp =>
      model -> Cmd.Empty

    case Msg.Connect =>
      model.copy(error = None) -> refocusInput
    //, Cmd.batch [ WS.connect model.wsUrl, refocusInput ]

    case Msg.CloseAlerts =>
      model.copy(sub = None, unsub = None) -> refocusInput

    case Msg.InvalidSymbol(sl) =>
      model.copy(error = Some(s"Invalid symbol: $sl")) -> Cmd.Empty

    case Msg.SymbolChanged(sl) =>
      model.copy(symbol = sl) -> Cmd.Empty

    case Msg.Subscribe =>
      model.socketId match
        case Some(_) =>
          val nm       = model.copy(sub = Some(model.symbol), symbol = Symbol.XXXXXX)
          val in: WsIn = WsIn.Subscribe(model.symbol)
          nm -> model.ws.publish(in.asJson.noSpaces)
        case None =>
          disconnected(model)

    case Msg.Unsubscribe(symbol) =>
      model.socketId match
        case Some(_) =>
          val nm       = model.copy(unsub = Some(symbol), alerts = model.alerts - symbol)
          val in: WsIn = WsIn.Unsubscribe(model.symbol)
          nm -> Cmd.Batch(model.ws.publish(in.asJson.noSpaces), refocusInput)
        case None =>
          disconnected(model)

    case Msg.Recv(WsOut.Attached(sid, users)) =>
      model.copy(socketId = Some(sid), onlineUsers = users.toInt) -> Cmd.Empty

    case Msg.Recv(WsMsg.CloseConnection) =>
      model -> Cmd.Empty // WS.disconnect ()

    //case Msg.Recv(WsOut.ConnectionError(cause)) =>
    //model.copy(error = Some(s"Connection error: $cause")) -> Cmd.Empty

    case Msg.Recv(WsOut.Notification(t: Alert.TradeAlert)) =>
      model.copy(alerts = model.alerts.updated(t.symbol, t)) -> Cmd.Empty

    case Msg.Recv(WsOut.Notification(t: Alert.TradeUpdate)) =>
      model.copy(tradingStatus = t.status) -> Cmd.Empty

//case Msg.Recv(WsOut.SocketClose) =>
//model.copy(socketId = None) -> Cmd.Empty
