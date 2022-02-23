package trading.client

import trading.domain.*
import trading.ws.WsOut

enum WsMsg:
  case Error(msg: String)
  case Disconnected

enum Msg:
  case CloseAlerts
  case Connect
  case SymbolChanged(input: String)
  case Subscribe
  case Unsubscribe(symbol: Symbol)
  case Recv(in: WsOut)
  case ConnStatus(msg: WsMsg)
  case NoOp

case class Model(
    symbol: Symbol,
    ws: Option[WS],
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
