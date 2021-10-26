package trading.core.snapshots

import trading.domain.*
import trading.state.{ Prices, TradeState }

import cats.MonadThrow
import cats.effect.kernel.Resource
import cats.syntax.all.*
import dev.profunktor.redis4cats.effect.MkRedis
import dev.profunktor.redis4cats.{ Redis, RedisCommands }
import io.circe.parser.decode as jsonDecode

trait SnapshotReader[F[_]]:
  def latest: F[Option[TradeState]]

/** This model only allows for a single snappshots service running at a time. Thus, the snapshots service uses a
  * Failover subscription mode and it's recommended to run two instances: a main one, and a failover one.
  */
object SnapshotReader:
  def fromClient[F[_]: MonadThrow](
      redis: RedisCommands[F, String, String] // FIXME: this ain't a client
  ): SnapshotReader[F] =
    new SnapshotReader[F]:
      def latest: F[Option[TradeState]] =
        redis.keys("snapshot*").flatMap {
          _.traverseFilter { key =>
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
          }
            .map {
              case Nil => None
              case xs  => Some(TradeState(xs.toMap))
            }
        }

  def make[F[_]: MkRedis: MonadThrow](uri: RedisURI): Resource[F, SnapshotReader[F]] =
    Redis[F].utf8(uri.value).map(fromClient[F])
