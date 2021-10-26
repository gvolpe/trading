package trading.forecasts.store

import scala.concurrent.duration.*
import scala.util.control.NoStackTrace

import trading.domain.*

import cats.MonadThrow
import cats.effect.kernel.Resource
import cats.syntax.all.*
import dev.profunktor.redis4cats.effect.MkRedis
import dev.profunktor.redis4cats.effects.{ SetArg, SetArgs }
import dev.profunktor.redis4cats.{ Redis, RedisCommands }
import io.circe.parser.decode as jsonDecode
import io.circe.syntax.*

trait AuthorStore[F[_]]:
  def fetch(id: AuthorId): F[Option[Author]]
  def save(author: Author): F[Unit]

// Ideally this should be persisted in a proper database such as PostgreSQL but to keep things simple we use Redis.
object AuthorStore:
  final case class DuplicateAuthorError(name: AuthorName) extends NoStackTrace

  //TODO: configure expiration time
  def fromClient[F[_]: MonadThrow](
      redis: RedisCommands[F, String, String]
  ): AuthorStore[F] =
    new AuthorStore[F]:
      def fetch(id: AuthorId): F[Option[Author]] =
        redis.get(s"author-${id.show}").map(_.flatMap(jsonDecode[Author](_).toOption))

      def save(author: Author): F[Unit] =
        redis
          .set(
            s"author-${author.id.show}",
            author.asJson.noSpaces,
            SetArgs(SetArg.Existence.Nx, SetArg.Ttl.Ex(30.days))
          )
          .flatMap {
            DuplicateAuthorError(author.name).raiseError.unlessA(_)
          }

  def make[F[_]: MkRedis: MonadThrow](uri: RedisURI): Resource[F, AuthorStore[F]] =
    Redis[F].utf8(uri.value).map(fromClient[F])
