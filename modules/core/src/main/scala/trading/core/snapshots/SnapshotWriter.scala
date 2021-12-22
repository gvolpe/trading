package trading.core.snapshots

import trading.domain.*
import trading.state.TradeState

import cats.MonadThrow
import cats.effect.kernel.{ Async, Resource }
import cats.syntax.all.*
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.{ Log, MkRedis }
import dev.profunktor.redis4cats.{ Redis, RedisCommands }
import io.circe.syntax.*

trait SnapshotWriter[F[_]]:
  def save(state: TradeState): F[Unit]

object SnapshotWriter:
  def from[F[_]: MonadThrow](
      redis: RedisCommands[F, String, String],
      exp: KeyExpiration
  ): SnapshotWriter[F] = new:
    def save(state: TradeState): F[Unit] =
      redis.set(s"trading-status", state.status.show) *>
        state.prices.toList.traverse_ { case (symbol, prices) =>
          val key = s"snapshot-$symbol"
          redis.hSet(key, "ask", prices.ask.toList.asJson.noSpaces) *>
            redis.hSet(key, "bid", prices.bid.toList.asJson.noSpaces) *>
            redis.hSet(key, "high", prices.high.show) *>
            redis.hSet(key, "low", prices.low.show) *>
            redis.expire(key, exp.value)
        }

  def fromClient[F[_]: MkRedis: MonadThrow](
      client: RedisClient,
      exp: KeyExpiration
  ): Resource[F, SnapshotWriter[F]] =
    Redis[F].fromClient(client, RedisCodec.Utf8).map(from(_, exp))

  def make[F[_]: Async: Log: MonadThrow](
      uri: RedisURI,
      exp: KeyExpiration
  ): Resource[F, SnapshotWriter[F]] =
    RedisClient[F].from(uri.value).flatMap(fromClient[F](_, exp))
