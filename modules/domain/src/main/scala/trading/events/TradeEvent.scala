package trading.events

import trading.commands.TradeCommand
import trading.domain._

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
