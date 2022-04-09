package trading.lib

import trading.commands.SwitchCommand
import trading.domain.Alert
import trading.events.SwitchEvent

import cats.syntax.all.*
import dev.profunktor.pulsar.{ MessageKey, ShardKey }

trait Partition[A]:
  def key: A => MessageKey

object Partition:
  def apply[A: Partition]: Partition[A] = summon

  def default[A]: Partition[A] = new:
    val key: A => MessageKey = _ => MessageKey.Empty

  def by(s: String): MessageKey = MessageKey.Of(s)

  given Partition[SwitchCommand] with
    val key: SwitchCommand => MessageKey = {
      case _: SwitchCommand.Start => by("start")
      case _: SwitchCommand.Stop  => by("stop")
    }

  given Partition[SwitchEvent] with
    val key: SwitchEvent => MessageKey = {
      case _: SwitchEvent.Started => by("started")
      case _: SwitchEvent.Stopped => by("stopped")
      case _: SwitchEvent.Ignored => by("ignored")
    }

  given Partition[Alert] with
    val key: Alert => MessageKey = {
      case a: Alert.TradeAlert  => by(a.symbol.show)
      case a: Alert.TradeUpdate => by(a.status.show)
    }
