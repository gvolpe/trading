package trading.domain

import trading.ws.WsOut

import cats.{ Applicative, Eq, Show }
// FIXME: importing all `given` yield ambiguous implicits
import cats.derived.semiauto.{ coproductEq, product, productEq, * }
import cats.syntax.all.*
import io.circe.Codec
import monocle.Traversal

sealed trait Alert derives Codec.AsObject, Show:
  def id: AlertId
  def cid: CorrelationId
  def createdAt: Timestamp
  def wsOut: WsOut = WsOut.Notification(this)

object Alert:
  final case class TradeAlert(
      id: AlertId,
      cid: CorrelationId,
      alertType: AlertType,
      symbol: Symbol,
      askPrice: AskPrice,
      bidPrice: BidPrice,
      high: HighPrice,
      low: LowPrice,
      createdAt: Timestamp
  ) extends Alert

  final case class TradeUpdate(
      id: AlertId,
      cid: CorrelationId,
      status: TradingStatus,
      createdAt: Timestamp
  ) extends Alert

  // Eq instances are used for deduplication (we don't consider `cid` so the topic can be compacted more often)
  // FIXME: should not be necessary to use `show` on `alertType` (related to typeclass derivation)
  given Eq[TradeAlert] = Eq.instance { (x, y) =>
    x.alertType.show === y.alertType.show && x.symbol === y.symbol && x.askPrice === y.askPrice && x.bidPrice === y.bidPrice && x.high === y.high && x.low === y.low
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