package trading.domain

import trading.ws.WsOut

import cats.{ Applicative, Eq, Show }
import cats.derived.*
import cats.syntax.all.*
import io.circe.Codec
import monocle.Traversal

enum Alert derives Codec.AsObject, Show:
  def id: AlertId
  def cid: CorrelationId
  def createdAt: Timestamp
  def wsOut: WsOut = WsOut.Notification(this)

  case TradeAlert(
      id: AlertId,
      cid: CorrelationId,
      alertType: AlertType,
      symbol: Symbol,
      askPrice: AskPrice,
      bidPrice: BidPrice,
      high: HighPrice,
      low: LowPrice,
      createdAt: Timestamp
  )

  case TradeUpdate(
      id: AlertId,
      cid: CorrelationId,
      status: TradingStatus,
      createdAt: Timestamp
  )

object Alert:
  // Eq instances are used for topic compaction (we don't consider `cid` so the topic can be compacted more often)
  given Eq[TradeAlert] = Eq.instance { (x, y) =>
    x.alertType === y.alertType && x.symbol === y.symbol && x.askPrice === y.askPrice && x.bidPrice === y.bidPrice && x.high === y.high && x.low === y.low
  }

  given Eq[TradeUpdate] = Eq.instance { (x, y) =>
    x.status === y.status
  }

  given Eq[Alert] = Eq.instance {
    case (x: TradeAlert, y: TradeAlert)   => x === y
    case (x: TradeUpdate, y: TradeUpdate) => x === y
    case _                                => false
  }

  val _CorrelationId: Traversal[Alert, CorrelationId] = new:
    def modifyA[F[_]: Applicative](f: CorrelationId => F[CorrelationId])(s: Alert): F[Alert] =
      f(s.cid).map { newCid =>
        s match
          case c: TradeAlert  => c.copy(cid = newCid)
          case c: TradeUpdate => c.copy(cid = newCid)
      }
