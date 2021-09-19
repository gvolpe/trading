package trading.core.snapshots

import trading.state.TradeState

import cats.MonadThrow
import cats.effect.kernel.Resource
import cats.syntax.all._
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.MkRedis

trait SnapshotReader[F[_]] {
  def latest: F[Option[TradeState]]
}

object SnapshotReader {
  def make[F[_]: MkRedis: MonadThrow]: Resource[F, SnapshotReader[F]] =
    Redis[F].utf8("redis://localhost").map { redis =>
      new SnapshotReader[F] {
        def latest: F[Option[TradeState]] =
          // maybe HSET "EURPLN" "ask" 4.5679 & HSET "EURPLN" "bid" 4.54874
          /* keys => TODO: filter by symbol according to shard key - NOT SURE IF POSSIBLE */
          redis.keys("snapshot*").flatMap {
            _.traverse { key =>
              redis.hGetAll(key).map { kv =>
                val ask    = kv.get("ask").map(BigDecimal.apply).getOrElse(BigDecimal(0.0))
                val bid    = kv.get("bid").map(BigDecimal.apply).getOrElse(BigDecimal(0.0))
                val symbol = key.split("-").apply(1) // FIXME: potentially unsafe
                // FIXME: ask and bids are now lists
                symbol -> (List(ask) -> List(bid))
              }
            }
              .map {
                case Nil => None
                case xs  => Some(TradeState(xs.toMap))
              }
          }
      }
    }
}
