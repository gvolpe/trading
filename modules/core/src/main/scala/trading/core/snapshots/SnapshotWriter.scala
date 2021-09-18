package trading.core.snapshots

import trading.state.TradeState

import cats.MonadThrow
import cats.effect.kernel.Resource
import cats.syntax.all._
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.MkRedis

trait SnapshotWriter[F[_]] {
  def save(state: TradeState): F[Unit]
}

object SnapshotWriter {
  // Eventually this should be stored in Redis (if set to store in disk) or Cassandra
  // we should persist one key-value per symbol, where key = symbol
  // maybe HSET "EURPLN" "ask" 4.5679 & HSET "EURPLN" "bid" 4.54874
  def make[F[_]: MkRedis: MonadThrow]: Resource[F, SnapshotWriter[F]] =
    Redis[F].utf8("redis://localhost").map { redis =>
      new SnapshotWriter[F] {
        def save(state: TradeState): F[Unit] =
          state.prices.toList.traverse_ { case (symbol, (ask, bid)) =>
            redis.hSet(s"snapshot-$symbol", "ask", ask.toString) *>
              redis.hSet(s"snapshot-$symbol", "bid", bid.toString)
          }
      }
    }
}
