package trading.lib

import java.nio.charset.StandardCharsets.UTF_8

import trading.commands.{ ForecastCommand, TradeCommand }
import trading.domain.Alert
import trading.events.TradeEvent

import cats.syntax.all.*
import dev.profunktor.pulsar.ShardKey

/** A shard corresponds to the `orderingKey` of a Pulsar `Message`, which is used for routing in `KeyShared`
  * subscriptions.
  *
  * For topic compaction, see the [[Compaction]] typeclass.
  */
trait Shard[A]:
  def key: A => ShardKey

object Shard:
  def apply[A: Shard]: Shard[A] = summon

  def default[A]: Shard[A] = new:
    val key: A => ShardKey = _ => ShardKey.Default

  def by(s: String): ShardKey = Shard[String].key(s)

  given Shard[String] with
    val key: String => ShardKey =
      str => ShardKey.Of(str.getBytes(UTF_8))

  given Shard[TradeCommand] with
    val key: TradeCommand => ShardKey =
      cmd => Shard.by(cmd.symbol.show)

  given Shard[TradeEvent] with
    val key: TradeEvent => ShardKey =
      ev => Shard[TradeCommand].key(ev.command)

  given Shard[Alert] with
    val key: Alert => ShardKey = {
      case a: Alert.TradeAlert  => Shard.by(a.symbol.show)
      case a: Alert.TradeUpdate => Shard.by(a.status.show)
    }

  given Shard[ForecastCommand] with
    val key: ForecastCommand => ShardKey = fc => Shard.by(fc.cid.show)
