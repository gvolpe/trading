package trading.core.snapshots

import trading.domain.*
import trading.lib.Consumer
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
  def getLastId: F[Option[Consumer.MsgId]]
  def latest: F[Option[(TradeState, Consumer.MsgId)]]

object SnapshotReader:
  def from[F[_]: MonadThrow](
      redis: RedisCommands[F, String, String]
  ): SnapshotReader[F] = new:
    def getLastId: F[Option[Consumer.MsgId]] =
      redis.get("trading-last-id")

    def getStatus: F[TradingStatus] =
      redis.get("trading-status").map {
        _.flatMap(TradingStatus.from).getOrElse(TradingStatus.On)
      }

    def latest: F[Option[(TradeState, Consumer.MsgId)]] =
      (getStatus, getLastId, redis.keys("snapshot*")).tupled.flatMap { (ts, id, sn) =>
        sn.traverseFilter { key =>
          redis.hGetAll(key).map { kv =>
            val ask  = kv.get("ask").toList.flatMap(jsonDecode[List[(AskPrice, Quantity)]](_).toList).flatten
            val bid  = kv.get("bid").toList.flatMap(jsonDecode[List[(BidPrice, Quantity)]](_).toList).flatten
            val high = Price(kv.get("high").flatMap(_.toDoubleOption).getOrElse(0.0))
            val low  = Price(kv.get("low").flatMap(_.toDoubleOption).getOrElse(0.0))

            Either
              .catchNonFatal(key.split("-").apply(1)) // get symbol
              .toOption
              .map(Symbol.unsafeFrom(_) -> Prices(ask.toMap, bid.toMap, high, low))
          }
        }.map {
          case Nil => None
          case xs =>
            id.map(TradeState(ts, xs.toMap) -> _)
        }
      }

  def fromClient[F[_]: MkRedis: MonadThrow](
      client: RedisClient
  ): Resource[F, SnapshotReader[F]] =
    Redis[F].fromClient(client, RedisCodec.Utf8).map(from)

  def make[F[_]: Async: Log: MonadThrow](uri: RedisURI): Resource[F, SnapshotReader[F]] =
    RedisClient[F].from(uri.value).flatMap(fromClient[F])
