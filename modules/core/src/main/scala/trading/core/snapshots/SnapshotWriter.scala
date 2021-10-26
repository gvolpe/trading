package trading.core.snapshots

import trading.domain.KeyExpiration
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
      redis: RedisCommands[F, String, String], // FIXME: this is not a client
      exp: KeyExpiration
  ): SnapshotWriter[F] =
    new SnapshotWriter[F]:
      def save(state: TradeState): F[Unit] =
        state.prices.toList.traverse_ { case (symbol, prices) =>
          val key = s"snapshot-$symbol"
          redis.hSet(key, "ask", prices.ask.toList.asJson.noSpaces) *>
            redis.hSet(key, "bid", prices.bid.toList.asJson.noSpaces) *>
            redis.hSet(key, "high", prices.high.show) *>
            redis.hSet(key, "low", prices.low.show) *>
            redis.expire(key, exp.value)
        }

  def make[F[_]: MkRedis: MonadThrow](
      exp: KeyExpiration
  ): Resource[F, SnapshotWriter[F]] =
    Redis[F].utf8("redis://localhost").map(cli => fromClient[F](cli, exp))
