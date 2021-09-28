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

// POINTS OF FAILURE (to consider in distributed systems)
//
// A TradeCommand is consumed (and auto-acked), and the service fails before publishing the corresponding
// CommandExecuted event. In this case, we would lose such event, even when running multiple instances.
//
// To solve it, we need manual acks. Once a TradeCommand is consumed, if the services fails before publishing
// the event, then any other instance will pick it up.
//
// However, there is another potential issue. E.g. once the TradeCommand is consumed and the event is
// published, what happens if we fail to ack and the service fails at this point? Same, other instances
// will pick it up (as it is marked as unacked), but the event generated from such command was already
// processed (by the alerts and snapshots services), so this would be a duplicate TradeEvent.
//
// Event consumers should be able to de-duplicate such events, for which they can keep track of the processed
// command ids present in the events.
object TradeEvent {
  final case class CommandExecuted(
      command: TradeCommand,
      timestamp: Timestamp
  ) extends TradeEvent
}
