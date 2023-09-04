package trading.lib

import trading.commands.SwitchCommand
import trading.domain.{ Alert, PriceUpdate }
import trading.events.SwitchEvent

import cats.syntax.all.*
import dev.profunktor.pulsar.MessageKey

/**
 * A compaction key corresponds to the (partitioning) `key` of a Pulsar `Message`, which is used for topic compaction
 * as well as for routing messages when the `orderingKey` is absent.
 *
 * For `KeyShared` subscriptions, see the [[Shard]] typeclass.
 */
trait Compaction[A]:
  def key: A => MessageKey

object Compaction:
  def apply[A: Compaction]: Compaction[A] = summon

  def default[A]: Compaction[A] = new:
    val key: A => MessageKey = _ => MessageKey.Empty

  def by(s: String): MessageKey = MessageKey.Of(s)

  given Compaction[PriceUpdate] with
    val key: PriceUpdate => MessageKey = p => by(p.symbol.show)

  given Compaction[SwitchCommand] with
    val key: SwitchCommand => MessageKey = {
      case _: SwitchCommand.Start => by("start")
      case _: SwitchCommand.Stop  => by("stop")
    }

  given Compaction[SwitchEvent] with
    val key: SwitchEvent => MessageKey = {
      case _: SwitchEvent.Started => by("started")
      case _: SwitchEvent.Stopped => by("stopped")
      case _: SwitchEvent.Ignored => by("ignored")
    }

  given Compaction[Alert] with
    val key: Alert => MessageKey = {
      case a: Alert.TradeAlert  => by(a.symbol.show)
      case a: Alert.TradeUpdate => by(a.status.show)
    }
