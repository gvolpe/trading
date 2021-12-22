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

trait AuthorStore[F[_]]:
  def fetch(id: AuthorId): F[Option[Author]]
  def save(author: Author): F[Unit]
  def addForecast(id: AuthorId, fid: ForecastId): F[Unit]

// Ideally this should be persisted in a proper database such as PostgreSQL but to keep things simple we use Redis.
object AuthorStore:
  case object AuthorNotFound                              extends NoStackTrace
  final case class DuplicateAuthorError(name: AuthorName) extends NoStackTrace

  def from[F[_]: MonadThrow](
      redis: RedisCommands[F, String, String],
      exp: Config.AuthorExpiration
  ): AuthorStore[F] = new:
    def fetch(id: AuthorId): F[Option[Author]] =
      val key1   = s"author-${id.show}"
      val key2   = s"author-forecasts-${id.show}"
      val fields = List("name", "website")
      redis.hmGet(key1, fields*).flatMap { kv =>
        kv.nonEmpty
          .guard[Option]
          .as {
            redis.sMembers(key2).map { fc =>
              val n = AuthorName(kv.getOrElse(fields(0), ""))
              val w = kv.get(fields(1)).map(Website(_))
              val f = fc.map(ForecastId.unsafeFrom)
              Author(id, n, w, f)
            }
          }
          .sequence
      }

    def save(author: Author): F[Unit] =
      val key1 = s"author-${author.id.show}"
      val key2 = s"author-forecasts-${author.id.show}"

      val saveForecast =
        redis.sAdd(key2, author.forecasts.map(_.show).toSeq*) *> redis.expire(key2, exp.value)

      for
        x <- redis.hSetNx(key1, "name", author.name.show)
        _ <- DuplicateAuthorError(author.name).raiseError.unlessA(x)
        _ <- author.website.traverse_(w => redis.hSet(key1, "website", w.show))
        _ <- redis.expire(key1, exp.value)
        _ <- saveForecast.whenA(author.forecasts.nonEmpty)
      yield ()

    def addForecast(id: AuthorId, fid: ForecastId): F[Unit] =
      for
        kv <- redis.hGetAll(s"author-${id.show}")
        _  <- AuthorNotFound.raiseError.whenA(kv.isEmpty)
        key = s"author-forecasts-${id.show}"
        _ <- redis.sAdd(key, fid.show)
        _ <- redis.expire(key, exp.value)
      yield ()

  def make[F[_]: MkRedis: MonadThrow](
      client: RedisClient,
      exp: Config.AuthorExpiration
  ): Resource[F, AuthorStore[F]] =
    Redis[F].fromClient(client, RedisCodec.Utf8).map(from(_, exp))

  def make[F[_]: Async: Log](
      uri: RedisURI,
      exp: Config.AuthorExpiration
  ): Resource[F, AuthorStore[F]] =
    RedisClient[F].from(uri.value).flatMap(make[F](_, exp))
