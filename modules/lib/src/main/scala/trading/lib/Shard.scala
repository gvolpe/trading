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

  // Start and Stop should go to the same shard responsible for the trading switch
  given Shard[TradeCommand] with
    val key: TradeCommand => ShardKey =
      cmd =>
        Shard[String].key {
          cmd match
            case _: TradeCommand.Start | _: TradeCommand.Stop =>
              "trading-status-shard"
            case _ =>
              TradeCommand._Symbol.get(cmd).get.show
        }
