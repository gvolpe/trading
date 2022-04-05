package trading.lib

import java.nio.charset.StandardCharsets.UTF_8

import trading.commands.{ ForecastCommand, TradeCommand }
import trading.domain.Alert
import trading.events.TradeEvent

import cats.syntax.all.*
import dev.profunktor.pulsar.{ MessageKey, ShardKey }

trait Partition[A]:
  def key: A => MessageKey

object Partition:
  def apply[A: Partition]: Partition[A] = summon

  def default[A]: Partition[A] = new:
    val key: A => MessageKey = _ => MessageKey.Empty

  given [A](using ev: Shard[A]): Partition[A] with
    val key: A => MessageKey = ev.key(_) match
      case ShardKey.Default => MessageKey.Empty
      case ShardKey.Of(bs)  => MessageKey.Of(new String(bs, UTF_8))
