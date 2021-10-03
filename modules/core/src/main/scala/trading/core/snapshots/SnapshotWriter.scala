package trading.core.snapshots

import trading.state.TradeState

import cats.MonadThrow
import cats.effect.kernel.Resource
import cats.syntax.all.*
import dev.profunktor.redis4cats.effect.MkRedis
import dev.profunktor.redis4cats.{ Redis, RedisCommands }
import io.circe.syntax.*

trait SnapshotWriter[F[_]]:
  def save(state: TradeState): F[Unit]

object SnapshotWriter:
  def fromClient[F[_]: MonadThrow](
      redis: RedisCommands[F, String, String]
  ): SnapshotWriter[F] =
    // TODO: can we use `with` def save... ?
    new SnapshotWriter[F] {
      def save(state: TradeState): F[Unit] =
        state.prices.toList.traverse_ { case (symbol, prices) =>
          redis.hSet(s"snapshot-$symbol", "ask", prices.ask.toList.asJson.noSpaces) *>
            redis.hSet(s"snapshot-$symbol", "bid", prices.bid.toList.asJson.noSpaces) *>
            redis.hSet(s"snapshot-$symbol", "high", prices.high.toString) *>
            redis.hSet(s"snapshot-$symbol", "low", prices.low.toString)
        }
    }

  def make[F[_]: MkRedis: MonadThrow]: Resource[F, SnapshotWriter[F]] =
    Redis[F].utf8("redis://localhost").map(fromClient[F])
