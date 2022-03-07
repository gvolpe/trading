package trading.client

import trading.domain.*
import trading.ws.WsOut

import tyrian.websocket.WebSocket

enum WsMsg:
  case Error(msg: String)
  case Connecting
  case Connected(ws: WebSocket)
  case Heartbeat
  case Disconnected

  def asMsg: Msg = Msg.ConnStatus(this)

enum Msg:
  case CloseAlerts
  case SymbolChanged(input: String)
  case Subscribe
  case Unsubscribe(symbol: Symbol)
  case Recv(in: WsOut)
  case ConnStatus(msg: WsMsg)
  case FocusError(id: String)
  case NoOp

case class Model(
    symbol: Symbol,
    input: String,
    ws: Option[WebSocket],
    wsUrl: String,
    socketId: Option[SocketId],
    onlineUsers: Int,
    alerts: Map[Symbol, Alert],
    tradingStatus: TradingStatus,
    sub: Option[Symbol],
    unsub: Option[Symbol],
    error: Option[String]
)

object Model:
  def init = Model(
    symbol = Symbol.XXXXXX,
    input = "",
    ws = None,
    wsUrl = "ws://localhost:9000/v1/ws",
    socketId = None,
    onlineUsers = 0,
    alerts = Map.empty,
    tradingStatus = TradingStatus.On,
    sub = None,
    unsub = None,
    error = None
  )
