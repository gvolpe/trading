package trading.domain

import trading.ws.WsOut

import cats.{ Applicative, Show }
import cats.derived.*
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

  val _CorrelationId: Traversal[Alert, CorrelationId] = new:
    def modifyA[F[_]: Applicative](f: CorrelationId => F[CorrelationId])(s: Alert): F[Alert] =
      f(s.cid).map { newCid =>
        s match
          case c: TradeAlert  => c.copy(cid = newCid)
          case c: TradeUpdate => c.copy(cid = newCid)
      }
