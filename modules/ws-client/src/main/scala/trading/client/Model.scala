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

final case class Model(
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

object Dummy:
  import java.time.Instant
  import java.util.UUID

  import trading.domain.Alert.TradeAlert
  import trading.domain.AlertType.*
  import trading.domain.Symbol.*

  val id  = AlertId(UUID.fromString("7334c008-8089-4b07-8995-118582919b50"))
  val cid = CorrelationId(UUID.fromString("30454474-e16f-4807-997c-c168854aadda"))
  val ts  = Timestamp(Instant.parse("2022-10-03T14:00:00.00Z"))

  given Conversion[Double, Price] = Price(_)

  val alerts: Map[Symbol, Alert] =
    Map(
      EURUSD -> TradeAlert(id, cid, Sell, EURUSD, 1.287434123, 1.3567576891, 1.4712312454, 1.23545623114, ts),
      CHFEUR -> TradeAlert(id, cid, Buy, CHFEUR, 1.301236451, 1.4328765419, 1.4789877536, 1.27054836753, ts),
      CHFGBP -> TradeAlert(id, cid, Buy, CHFEUR, 1.301236451, 1.4328765419, 1.4789877536, 1.27054836753, ts),
      GBPUSD -> TradeAlert(id, cid, StrongSell, GBPUSD, 2.487465452, 2.7344545629, 2.9983565471, 2.21236312235, ts),
      EURPLN -> TradeAlert(id, cid, Neutral, EURPLN, 4.691272348, 4.4534524323, 4.8347145275, 3.83476129853, ts),
      AUDCAD -> TradeAlert(id, cid, StrongBuy, AUDCAD, 10.209676347, 10.3723136644, 10.5430958726, 10.01236543289, ts)
    )

object Model:
  def init = Model(
    symbol = mempty,
    input = mempty,
    socket = TradingSocket.init,
    onlineUsers = mempty,
    alerts = Map.empty, // Dummy.alerts
    tradingStatus = TradingStatus.On,
    sub = None,
    unsub = None,
    error = None
  )

  val _SocketId: Lens[Model, Option[SocketId]] =
    Focus[Model](_.socket).andThen(Focus[TradingSocket](_.id))
