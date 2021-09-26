package trading.domain

import trading.ws.WsOut

import derevo.cats.show
import derevo.circe.magnolia.{ decoder, encoder }
import derevo.derive

@derive(decoder, encoder, show)
sealed trait Alert {
  def symbol: Symbol
  def askPrice: AskPrice
  def bidPrice: BidPrice
  def high: Price
  def low: Price
  def wsOut: WsOut = WsOut.Notification(this)
}

object Alert {
  final case class StrongBuy(
      symbol: Symbol,
      askPrice: AskPrice,
      bidPrice: BidPrice,
      high: Price,
      low: Price
  ) extends Alert

  final case class StrongSell(
      symbol: Symbol,
      askPrice: AskPrice,
      bidPrice: BidPrice,
      high: Price,
      low: Price
  ) extends Alert

  final case class Neutral(
      symbol: Symbol,
      askPrice: AskPrice,
      bidPrice: BidPrice,
      high: Price,
      low: Price
  ) extends Alert

  final case class Buy(
      symbol: Symbol,
      askPrice: AskPrice,
      bidPrice: BidPrice,
      high: Price,
      low: Price
  ) extends Alert

  final case class Sell(
      symbol: Symbol,
      askPrice: AskPrice,
      bidPrice: BidPrice,
      high: Price,
      low: Price
  ) extends Alert
}
