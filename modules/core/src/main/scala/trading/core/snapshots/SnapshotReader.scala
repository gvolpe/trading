package trading.core.snapshots

import trading.domain.*
import trading.state.{ Prices, TradeState }

import cats.MonadThrow
import cats.effect.kernel.{ Async, Resource }
import cats.syntax.all.*
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.{ Log, MkRedis }
import dev.profunktor.redis4cats.{ Redis, RedisCommands }
import io.circe.parser.decode as jsonDecode

trait SnapshotReader[F[_]]:
  def latest: F[Option[TradeState]]

/** This model only allows for a single snappshots service running at a time. Thus, the snapshots service uses a
  * Failover subscription mode and it's recommended to run two instances: a main one, and a failover one.
  */
object SnapshotReader:
  def fromClient[F[_]: MkRedis: MonadThrow](
      client: RedisClient
  ): Resource[F, SnapshotReader[F]] =
    Redis[F].fromClient(client, RedisCodec.Utf8).map { redis =>
      new:
        def latest: F[Option[TradeState]] =
          (redis.get("trading-status"), redis.keys("snapshot*")).tupled.flatMap { (st, sn) =>
            sn.traverseFilter { key =>
              redis.hGetAll(key).map { kv =>
                val ask  = kv.get("ask").toList.flatMap(jsonDecode[List[(AskPrice, Quantity)]](_).toList).flatten
                val bid  = kv.get("bid").toList.flatMap(jsonDecode[List[(BidPrice, Quantity)]](_).toList).flatten
                val high = Price(kv.get("high").flatMap(_.toDoubleOption).getOrElse(0.0))
                val low  = Price(kv.get("low").flatMap(_.toDoubleOption).getOrElse(0.0))

                Either
                  .catchNonFatal(key.split("-").apply(1)) // get symbol
                  .toOption
                  .map(Symbol(_) -> Prices(ask.toMap, bid.toMap, high, low))
              }
            }.map {
              case Nil => None
              case xs =>
                val ts = st.flatMap(TradingStatus.from).getOrElse(TradingStatus.On)
                TradeState(ts, xs.toMap).some
            }
          }
    }

  def make[F[_]: Async: Log: MonadThrow](uri: RedisURI): Resource[F, SnapshotReader[F]] =
    RedisClient[F].from(uri.value).flatMap(fromClient[F])
