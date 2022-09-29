package trading.client

import trading.Newtype
import trading.domain.*
import trading.ws.WsOut

import cats.Monoid
import cats.effect.IO
import cats.syntax.all.*
import monocle.{ Focus, Lens }

//import tyrian.websocket.WebSocket

type InputText = InputText.Type
object InputText extends Newtype[String]:
  given Monoid[InputText] = derive

type WsUrl = WsUrl.Type
object WsUrl extends Newtype[String]

type ElemId = ElemId.Type
object ElemId extends Newtype[String]

given Conversion[InputText, String] = _.value
given Conversion[WsUrl, String]     = _.value

enum WsMsg:
  case Error(msg: String)
  case Connecting
  case Connected(ws: WebSocket[IO])
  case Heartbeat
  case Disconnected(code: Int, reason: String)

  def asMsg: Msg = Msg.ConnStatus(this)

enum Msg:
  case CloseAlerts
  case SymbolChanged(input: InputText)
  case Subscribe
  case Unsubscribe(symbol: Symbol)
  case Recv(in: WsOut)
  case ConnStatus(msg: WsMsg)
  case FocusError(id: ElemId)
  case NoOp

case class Model(
    symbol: Symbol,
    input: InputText,
    socket: TradingSocket,
    onlineUsers: Int,
    alerts: Map[Symbol, Alert],
    tradingStatus: TradingStatus,
    sub: Option[Symbol],
    unsub: Option[Symbol],
    error: Option[String]
)

object Model:
  def init = Model(
    symbol = mempty,
    input = mempty,
    socket = TradingSocket.init,
    onlineUsers = mempty,
    alerts = Map.empty,
    tradingStatus = TradingStatus.On,
    sub = None,
    unsub = None,
    error = None
  )

  val _SocketId: Lens[Model, Option[SocketId]] =
    Focus[Model](_.socket).andThen(Focus[TradingSocket](_.id))
