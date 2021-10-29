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
  def fromClient[F[_]: MkRedis: MonadThrow](
      client: RedisClient,
      exp: KeyExpiration
  ): Resource[F, SnapshotWriter[F]] =
    Redis[F].fromClient(client, RedisCodec.Utf8).map { redis =>
      new SnapshotWriter[F]:
        def save(state: TradeState): F[Unit] =
          state.prices.toList.traverse_ { case (symbol, prices) =>
            val key1 = s"snapshot-$symbol"
            val key2 = s"trading-status"
            redis.hSet(key1, "ask", prices.ask.toList.asJson.noSpaces) *>
              redis.hSet(key1, "bid", prices.bid.toList.asJson.noSpaces) *>
              redis.hSet(key1, "high", prices.high.show) *>
              redis.hSet(key1, "low", prices.low.show) *>
              redis.expire(key1, exp.value) *>
              redis.set(key2, state.status.show)
          }
    }

  def make[F[_]: Async: Log: MonadThrow](
      uri: RedisURI,
      exp: KeyExpiration
  ): Resource[F, SnapshotWriter[F]] =
    RedisClient[F].from(uri.value).flatMap(fromClient[F](_, exp))
