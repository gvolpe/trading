package trading.events

import trading.commands.TradeCommand
import trading.domain.*

import io.circe.Codec

sealed trait TradeEvent derives Codec.AsObject:
  def id: EventId
  def command: TradeCommand
  def timestamp: Timestamp

object TradeEvent:
  final case class CommandExecuted(
      id: EventId,
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
// The only downside of this approach is that any service that reads snapshots at startup such as `alerts`
// and `processor`, will be reading an older snapshot version if they restart whenever the `snapshots`
// service is down (which should be extremely rare). This is a trade-off that we must accept in a distributed
// system. The good news is that this "eventual inconsistency" will only last as long as the `snapshots`
// service is down, which should rarely happen too, but we need to think about the edge cases.
//
// Once `snapshots` is back up, we must restart all instances of `processor` and `alerts` so they start up
// from a good snapshots version. Hey, distributed systems are hard!
