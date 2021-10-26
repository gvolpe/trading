package trading.forecasts.store

import scala.concurrent.duration.*
import scala.util.control.NoStackTrace

import trading.domain.*

import cats.MonadThrow
import cats.effect.kernel.{Async ,Resource}
import cats.syntax.all.*
import dev.profunktor.redis4cats.codecs.Codecs
import dev.profunktor.redis4cats.codecs.splits.*
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log.Stdout.*
import dev.profunktor.redis4cats.effect.MkRedis
import dev.profunktor.redis4cats.effects.{ SetArg, SetArgs }
import dev.profunktor.redis4cats.{ Redis, RedisCommands }
import io.circe.parser.decode as jsonDecode
import io.circe.syntax.*

trait ForecastStore[F[_]]:
  //def fetchAll(id: AuthorId): F[List[Forecast]] // not needed for now
  def fetch(id: AuthorId, fid: ForecastId): F[Option[Forecast]]
  def save(forecast: Forecast): F[Unit]
  def upvote(id: AuthorId, fid: ForecastId): F[Unit]
  def downvote(id: AuthorId, fid: ForecastId): F[Unit]

// Ideally this should be persisted in a proper database such as PostgreSQL but to keep things simple we use Redis.
object ForecastStore:
  private val longCodec: RedisCodec[String, Long] =
    Codecs.derive(RedisCodec.Utf8, stringLongEpi)

  def fromClient[F[_]: MkRedis: MonadThrow](
      client: RedisClient
  ): Resource[F, ForecastStore[F]] =
    (Redis[F].fromClient(client, RedisCodec.Utf8), Redis[F].fromClient(client, longCodec)).mapN { (redis, redisN) =>
      new ForecastStore[F]:
        def fetch(id: AuthorId, fid: ForecastId): F[Option[Forecast]] =
          val fields = List(
            s"forecast-${fid.show}-description",
            s"forecast-${fid.show}-tag",
            s"forecast-${fid.show}-score",
            s"forecast-${fid.show}-symbol"
          )
          redis.hmGet(s"author-${id.show}", fields*).map { kv =>
            kv.nonEmpty.guard[Option].as {
              val d = kv.getOrElse(fields(0), "No description")
              val t = kv.get(fields(1)).map(ForecastTag.from).getOrElse(ForecastTag.Unknown)
              val c = ForecastScore(kv.get(fields(2)).flatMap(_.toIntOption).getOrElse(0))
              val s = Symbol(kv.getOrElse(fields(3), ""))
              Forecast(fid, id, s, t, d, c)
            }
          }

        def save(fc: Forecast): F[Unit] =
          val key = s"author-${fc.authorId.show}"
          val values = Map(
            s"forecast-${fc.id.show}-description" -> fc.description,
            s"forecast-${fc.id.show}-tag" -> fc.tag.show,
            s"forecast-${fc.id.show}-score" -> fc.score.show
          )
          redis.hmSet(key, values) <* redis.expire(key, 90.days) // TODO: Make it configurable

        private def castVote(id: AuthorId, fid: ForecastId, value: Int): F[Unit] =
          redisN.hIncrBy(s"author-${id.show}", s"forecast-${fid.show}-score", value).void

        def upvote(id: AuthorId, fid: ForecastId): F[Unit] =
          castVote(id, fid, 1)

        def downvote(id: AuthorId, fid: ForecastId): F[Unit] =
          castVote(id, fid, -1)
    }

  def make[F[_]: Async](uri: RedisURI): Resource[F, ForecastStore[F]] =
    RedisClient[F].from(uri.value).flatMap(fromClient[F])
