package trading.events

import trading.commands.TradeCommand
import trading.domain._

import derevo.circe.magnolia.{ decoder, encoder }
import derevo.derive

@derive(decoder, encoder)
sealed trait TradeEvent {
  def command: TradeCommand
  def timestamp: Timestamp
}

object TradeEvent {
  final case class CommandExecuted(
      command: TradeCommand,
      timestamp: Timestamp
  ) extends TradeEvent
}
