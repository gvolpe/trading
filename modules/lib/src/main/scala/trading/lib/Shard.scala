package trading.lib

import java.nio.charset.StandardCharsets.UTF_8

import trading.commands.TradeCommand

import dev.profunktor.pulsar.ShardKey

trait Shard[A]:
  def key: A => ShardKey

object Shard:
  def apply[A: Shard]: Shard[A] = summon

  def default[A]: Shard[A] =
    new Shard[A]:
      val key: A => ShardKey = _ => ShardKey.Default

  given Shard[TradeCommand] with
    val key: TradeCommand => ShardKey =
      cmd => ShardKey.Of(cmd.symbol.value.getBytes(UTF_8))
