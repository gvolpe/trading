package trading.domain

import trading.ws.WsOut

import derevo.cats.show
import derevo.circe.magnolia.{ decoder, encoder }
import derevo.derive

@derive(decoder, encoder, show)
final case class Alert(
    alertType: AlertType,
    symbol: Symbol,
    askPrice: AskPrice,
    bidPrice: BidPrice,
    high: Price,
    low: Price
) {
  def wsOut: WsOut = WsOut.Notification(this)
}
