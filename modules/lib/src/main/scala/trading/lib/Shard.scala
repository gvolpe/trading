package trading.lib

import java.nio.charset.StandardCharsets.UTF_8

import trading.commands.TradeCommand

import cats.syntax.all.*
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
      cmd =>
        ShardKey.Of {
          TradeCommand._Symbol
            .get(cmd)
            .map(_.value)
            .getOrElse(cmd.id.show)
            .getBytes(UTF_8)
        }
