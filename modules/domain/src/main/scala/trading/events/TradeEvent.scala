package trading.events

import trading.commands.TradeCommand
import trading.domain.*

import io.circe.Codec

sealed trait TradeEvent derives Codec.AsObject:
  def command: TradeCommand
  def timestamp: Timestamp

object TradeEvent:
  // TODO: Missing event id
  final case class CommandExecuted(
      command: TradeCommand,
      timestamp: Timestamp
  ) extends TradeEvent

// POINTS OF FAILURE (to consider in distributed systems)
//
// A TradeCommand is consumed (and auto-acked), and the service fails before publishing the corresponding
// CommandExecuted event. In this case, we would lose such event, even when running multiple instances.
//
// To solve it, we need manual acks. Once a TradeCommand is consumed, if the service fails before publishing
// the event, then any other instance will pick it up.
//
// However, there is another potential issue. E.g. once the TradeCommand is consumed and the event is
// published, what happens if we fail to ack and the service fails at this point? Same, other instances
// will pick it up (as it is marked as unacked), but the event generated from such command was already
// processed (by the alerts and snapshots services), so this would be a duplicate TradeEvent.
//
// Event consumers should be able to de-duplicate such events (idempotent services), for which they can keep
// track of the processed command ids present in the events.
//
// SNAPSHOTS (potential failures)
//
// The snapshots service runs in fail-over mode. This means, there can only be at most one running instance
// at a time. If for some reason the service fails, and the fail-over instance also fails to start, there
// will not be snapshots processed during the downtime window, but it will process all the unacked events
// once the service is up again. For this we use manual unsubscribe in pulsar.
//
// The only downside of this approach is that if the `processor` service restarts whenever `snapshots` is down,
// it will start with an older snapshot, which will be outdated. THINK MORE ABOUT THIS.
