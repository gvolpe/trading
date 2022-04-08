package trading.lib

import java.nio.charset.StandardCharsets.UTF_8

import trading.commands.*
import trading.domain.Alert
import trading.events.*

import cats.syntax.all.*
import dev.profunktor.pulsar.ShardKey

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

  given Shard[SwitchCommand] with
    val key: SwitchCommand => ShardKey =
      _ => Shard.by("trading-status-shard")

  given Shard[SwitchEvent] with
    val key: SwitchEvent => ShardKey =
      _ => Shard.by("trading-status-shard")

  given Shard[Alert] with
    val key: Alert => ShardKey = {
      case a: Alert.TradeAlert  => Shard.by(a.symbol.show)
      case a: Alert.TradeUpdate => Shard.by(a.status.show)
    }

  given Shard[ForecastCommand] with
    val key: ForecastCommand => ShardKey = fc => Shard.by(fc.cid.show)
