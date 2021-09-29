package trading.core.snapshots

import trading.domain._
import trading.state.{ Prices, TradeState }

import cats.MonadThrow
import cats.effect.kernel.Resource
import cats.syntax.all._
import dev.profunktor.redis4cats.effect.MkRedis
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import io.circe.parser.{ decode => jsonDecode }

trait SnapshotReader[F[_]] {
  def latest: F[Option[TradeState]]
}

object SnapshotReader {
  def fromClient[F[_]: MonadThrow](
      redis: RedisCommands[F, String, String]
  ): SnapshotReader[F] =
    new SnapshotReader[F] {
      def latest: F[Option[TradeState]] =
        // maybe HSET "EURPLN" "ask" 4.5679 & HSET "EURPLN" "bid" 4.54874
        /* keys => TODO: filter by symbol according to shard key - NOT SURE IF POSSIBLE */
        redis.keys("snapshot*").flatMap {
          _.traverse { key =>
            redis.hGetAll(key).map { kv =>
              val ask    = kv.get("ask").toList.flatMap(jsonDecode[List[(AskPrice, Quantity)]](_).toList).flatten
              val bid    = kv.get("bid").toList.flatMap(jsonDecode[List[(BidPrice, Quantity)]](_).toList).flatten
              val symbol = key.split("-").apply(1) // FIXME: potentially unsafe
              symbol -> Prices(ask.toMap, bid.toMap, 0.0, 0.0) // FIXME: Read lows and highs
            }
          }
            .map {
              case Nil => None
              case xs  => Some(TradeState(xs.toMap))
            }
        }
    }

  def make[F[_]: MkRedis: MonadThrow]: Resource[F, SnapshotReader[F]] =
    Redis[F].utf8("redis://localhost").map(fromClient[F])
}
