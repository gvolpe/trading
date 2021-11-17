package trading.lib

import java.nio.charset.StandardCharsets.UTF_8

import trading.commands.TradeCommand

import cats.syntax.all.*
import dev.profunktor.pulsar.ShardKey

trait Shard[A]:
  def key: A => ShardKey

object Shard:
  def apply[A: Shard]: Shard[A] = summon

  def default[A]: Shard[A] = new:
    val key: A => ShardKey = _ => ShardKey.Default

  given Shard[String] with
    val key: String => ShardKey =
      str => ShardKey.Of(str.getBytes(UTF_8))

  given Shard[TradeCommand] with
    val key: TradeCommand => ShardKey =
      cmd =>
        Shard[String].key {
          TradeCommand._Symbol
            .get(cmd)
            .map(_.value)
            .getOrElse(cmd.id.show)
        }
