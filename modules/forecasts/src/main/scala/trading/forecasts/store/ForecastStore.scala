package trading.forecasts.store

import scala.concurrent.duration.*
import scala.util.control.NoStackTrace

import trading.domain.*
import trading.forecasts.Config

import cats.MonadThrow
import cats.effect.kernel.{ Async, Resource }
import cats.syntax.all.*
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.{ Log, MkRedis }
import dev.profunktor.redis4cats.effects.{ SetArg, SetArgs }
import dev.profunktor.redis4cats.{ Redis, RedisCommands }
import io.circe.parser.decode as jsonDecode
import io.circe.syntax.*

trait ForecastStore[F[_]]:
  def fetch(fid: ForecastId): F[Option[Forecast]]
  def save(forecast: Forecast): F[Unit]
  def castVote(fid: ForecastId, res: VoteResult): F[Unit]

// Ideally this should be persisted in a proper database such as PostgreSQL but to keep things simple we use Redis.
object ForecastStore:
  def from[F[_]: MonadThrow](
      redis: RedisCommands[F, String, String],
      exp: Config.ForecastExpiration
  ): ForecastStore[F] = new:
    def fetch(fid: ForecastId): F[Option[Forecast]] =
      val fields = List("desc", "tag", "score", "symbol")
      redis.hmGet(s"forecast-${fid.show}", fields*).map { kv =>
        kv.nonEmpty.guard[Option].as {
          val d = ForecastDescription(kv.getOrElse(fields(0), ""))
          val t = kv.get(fields(1)).map(ForecastTag.from).getOrElse(ForecastTag.Unknown)
          val c = ForecastScore(kv.get(fields(2)).flatMap(_.toIntOption).getOrElse(0))
          val s = Symbol(kv.getOrElse(fields(3), ""))
          Forecast(fid, s, t, d, c)
        }
      }

    def save(fc: Forecast): F[Unit] =
      val key = s"forecast-${fc.id.show}"
      val values = Map(
        "desc"   -> fc.description.show,
        "tag"    -> fc.tag.show,
        "score"  -> fc.score.show,
        "symbol" -> fc.symbol.show
      )
      redis.hmSet(key, values) <* redis.expire(key, exp.value)

    def castVote(fid: ForecastId, res: VoteResult): F[Unit] =
      redis.hIncrBy(s"forecast-${fid.show}", "score", res.asInt).void

  def make[F[_]: MkRedis: MonadThrow](
      client: RedisClient,
      exp: Config.ForecastExpiration
  ): Resource[F, ForecastStore[F]] =
    Redis[F].fromClient(client, RedisCodec.Utf8).map(from(_, exp))

  def make[F[_]: Async: Log](
      uri: RedisURI,
      exp: Config.ForecastExpiration
  ): Resource[F, ForecastStore[F]] =
    RedisClient[F].from(uri.value).flatMap(make[F](_, exp))
