package trading.core.snapshots

import trading.state.TradeState

import cats.MonadThrow
import cats.effect.kernel.Resource
import cats.syntax.all._
import dev.profunktor.redis4cats.effect.MkRedis
import dev.profunktor.redis4cats.{ Redis, RedisCommands }
import io.circe.syntax._

trait SnapshotWriter[F[_]] {
  def save(state: TradeState): F[Unit]
}

object SnapshotWriter {
  def fromClient[F[_]: MonadThrow](
      redis: RedisCommands[F, String, String]
  ): SnapshotWriter[F] =
    new SnapshotWriter[F] {
      def save(state: TradeState): F[Unit] =
        state.prices.toList.traverse_ { case (symbol, prices) =>
          redis.hSet(s"snapshot-$symbol", "ask", prices.ask.toList.asJson.noSpaces) *>
            redis.hSet(s"snapshot-$symbol", "bid", prices.bid.toList.asJson.noSpaces) *>
            redis.hSet(s"snapshot-$symbol", "high", prices.high.toString) *>
            redis.hSet(s"snapshot-$symbol", "low", prices.low.toString)
        }
    }
  // Eventually this should be stored in Redis (if set to store in disk) or Cassandra
  // we should persist one key-value per symbol, where key = symbol
  // maybe HSET "EURPLN" "ask" 4.5679 & HSET "EURPLN" "bid" 4.54874
  def make[F[_]: MkRedis: MonadThrow]: Resource[F, SnapshotWriter[F]] =
    Redis[F].utf8("redis://localhost").map(fromClient[F])
}
