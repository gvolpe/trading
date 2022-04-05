package trading.lib

import java.nio.charset.StandardCharsets.UTF_8

import trading.commands.{ ForecastCommand, TradeCommand }
import trading.domain.Alert
import trading.events.TradeEvent

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

  // Start and Stop should go to the same shard responsible for the trading switch
  given Shard[TradeCommand] with
    val key: TradeCommand => ShardKey = {
      case _: TradeCommand.Start | _: TradeCommand.Stop =>
        Shard.by("trading-status-shard")
      case cmd =>
        Shard.by(TradeCommand._Symbol.get(cmd).get.show)
    }

  given Shard[TradeEvent] with
    val key: TradeEvent => ShardKey =
      TradeEvent._Command.get(_) match
        case Some(cmd) => Shard[TradeCommand].key(cmd)
        case None      => Shard.by("trading-status-shard")

  given Shard[TradeEvent.Switch] with
    val key: TradeEvent.Switch => ShardKey = s => Shard[TradeEvent].key(s.getEvent)

  given Shard[Alert] with
    val key: Alert => ShardKey = {
      case a: Alert.TradeAlert  => Shard.by(a.symbol.show)
      case a: Alert.TradeUpdate => Shard.by(a.status.show)
    }

  given Shard[ForecastCommand] with
    val key: ForecastCommand => ShardKey = fc => Shard.by(fc.cid.show)
