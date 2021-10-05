package trading.domain

import trading.ws.WsOut

import cats.Show
import cats.derived.semiauto.*
import io.circe.Codec

final case class Alert(
    alertType: AlertType,
    symbol: Symbol,
    askPrice: AskPrice,
    bidPrice: BidPrice,
    high: Price,
    low: Price
) derives Codec.AsObject, Show:
  def wsOut: WsOut = WsOut.Notification(this)
