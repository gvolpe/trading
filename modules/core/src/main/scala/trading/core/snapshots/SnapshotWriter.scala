package trading.core.snapshots

import trading.domain.*
import trading.lib.Consumer
import trading.state.TradeState

import cats.effect.kernel.{ Async, Resource }
import cats.syntax.all.*
import dev.profunktor.redis4cats.{ Redis, RedisCommands }
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.hlist.*
import dev.profunktor.redis4cats.transactions.*
import io.circe.syntax.*

trait SnapshotWriter[F[_]]:
  def save(state: TradeState, id: Consumer.MsgId): F[Unit]
  def saveStatus(st: TradingStatus): F[Unit]

object SnapshotWriter:
  def from[F[_]: Async: Log](
      redis: RedisCommands[F, String, String],
      exp: KeyExpiration
  ): SnapshotWriter[F] = new:
    def saveStatus(st: TradingStatus): F[Unit] =
      redis.set("trading-status", st.show)

    def save(state: TradeState, id: Consumer.MsgId): F[Unit] =
      val tx = RedisTransaction(redis)
      val c1 = saveStatus(state.status)
      val c2 = redis.set("trading-last-id", id.serialize)

      // TODO: every inner command should be transactional (redis4cats limitation)
      val c3 = state.prices.toList.traverse_ { case (symbol, prices) =>
        val key = s"snapshot-$symbol"
        redis.hSet(key, "ask", prices.ask.toList.asJson.noSpaces) *>
          redis.hSet(key, "bid", prices.bid.toList.asJson.noSpaces) *>
          redis.hSet(key, "high", prices.high.show) *>
          redis.hSet(key, "low", prices.low.show) *>
          redis.expire(key, exp.value)
      }

      tx.exec(c1 :: c2 :: c3 :: HNil).void

  def fromClient[F[_]: Async: Log](
      client: RedisClient,
      exp: KeyExpiration
  ): Resource[F, SnapshotWriter[F]] =
    Redis[F].fromClient(client, RedisCodec.Utf8).map(from(_, exp))

  def make[F[_]: Async: Log](
      uri: RedisURI,
      exp: KeyExpiration
  ): Resource[F, SnapshotWriter[F]] =
    RedisClient[F].from(uri.value).flatMap(fromClient[F](_, exp))
