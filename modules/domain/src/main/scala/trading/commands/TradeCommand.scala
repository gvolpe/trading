package trading.commands

import trading.domain._

import derevo.circe.magnolia.{ decoder, encoder }
import derevo.derive

@derive(decoder, encoder)
sealed trait TradeCommand {
  def symbol: Symbol
  def tradeAction: TradeAction
  def source: Source
  def timestamp: Timestamp
}

object TradeCommand {
  final case class Add(
      symbol: Symbol,
      tradeAction: TradeAction,
      price: Price,
      quantity: Quantity,
      source: Source,
      timestamp: Timestamp
  ) extends TradeCommand

  final case class Delete(
      symbol: Symbol,
      tradeAction: TradeAction,
      price: Price,
      source: Source,
      timestamp: Timestamp
  ) extends TradeCommand
}
