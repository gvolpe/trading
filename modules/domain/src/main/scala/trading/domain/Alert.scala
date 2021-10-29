package trading.domain

import trading.ws.WsOut

import io.circe.Codec

sealed trait Alert derives Codec.AsObject:
  def wsOut: WsOut = WsOut.Notification(this)

object Alert:
  final case class TradeAlert(
      alertType: AlertType,
      symbol: Symbol,
      askPrice: AskPrice,
      bidPrice: BidPrice,
      high: Price,
      low: Price
  ) extends Alert

  final case class TradeUpdate(
      status: TradingStatus
  ) extends Alert
