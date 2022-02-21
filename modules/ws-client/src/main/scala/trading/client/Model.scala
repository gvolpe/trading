package trading.client

import trading.domain.*
import trading.ws.WsOut

import tyrian.websocket.WebSocket

sealed trait WsMsg
object WsMsg:
  case object CloseConnection extends WsMsg

sealed trait Msg
object Msg:
  case object CloseAlerts                  extends Msg
  case object Connect                      extends Msg
  case class SymbolChanged(symbol: Symbol) extends Msg
  case object Subscribe                    extends Msg
  case class Unsubscribe(symbol: Symbol)   extends Msg
  case class Recv(in: WsOut | WsMsg)       extends Msg
  case object NoOp                         extends Msg

case class Model(
    symbol: Option[Symbol],
    ws: WebSocket,
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
    symbol = None,
    ws = WebSocket("ws://localhost:9000/v1/ws"),
    socketId = None,
    onlineUsers = 0,
    alerts = Map.empty,
    tradingStatus = TradingStatus.On,
    sub = None,
    unsub = None,
    error = None
  )
