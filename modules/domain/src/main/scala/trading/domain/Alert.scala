package trading.domain

import trading.ws.WsOut

import io.circe.Codec

sealed trait Alert derives Codec.AsObject:
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
      high: Price,
      low: Price,
      createdAt: Timestamp
  ) extends Alert

  final case class TradeUpdate(
      id: AlertId,
      cid: CorrelationId,
      status: TradingStatus,
      createdAt: Timestamp
  ) extends Alert
