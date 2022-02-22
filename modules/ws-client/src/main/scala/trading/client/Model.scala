package trading.client

import trading.domain.*
import trading.ws.WsOut

sealed trait WsMsg
object WsMsg:
  case class Error(msg: String) extends WsMsg
  case object Disconnected      extends WsMsg

sealed trait Msg
object Msg:
  case object CloseAlerts                 extends Msg
  case object Connect                     extends Msg
  case class SymbolChanged(input: String) extends Msg
  case object Subscribe                   extends Msg
  case class Unsubscribe(symbol: Symbol)  extends Msg
  case class Recv(in: WsOut)              extends Msg
  case class ConnStatus(msg: WsMsg)    extends Msg
  case object NoOp                        extends Msg

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
