package demo.dedup

import trading.domain.*
import trading.lib.Time
import trading.state.{ DedupState, IdRegistry }

import cats.MonadThrow
import cats.effect.kernel.{ Async, Resource }
import cats.syntax.all.*
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.{ Log, MkRedis }
import dev.profunktor.redis4cats.{ Redis, RedisCommands }

// Not used in the project but left here to demonstrate how deduplication could be
// implemented for other brokers that do not support it natively (see also Conflicts).
trait DedupRegistry[F[_]]:
  def get: F[DedupState]
  def save(state: DedupState): F[Unit]

/** Registry of processed ids per application.
  *
  * The `get` function returns the union of all the sets of command ids that have been processed by all the instances of
  * the app (service).
  *
  * The `save` function persists the processed command ids of the current app instance, uniquely identified by its name
  * and id.
  *
  * Upon reading the keys, we reset all the ids' timestamp to make things easier, as we can avoid persisting everything
  * since these are short-lived and will expire either way.
  */
object DedupRegistry:
  def from[F[_]: MonadThrow: Time](
      redis: RedisCommands[F, String, String],
      appId: AppId,
      exp: KeyExpiration
  ): DedupRegistry[F] = new:
    def get: F[DedupState] =
      redis.keys(s"${appId.name}-*").flatMap {
        case Nil =>
          DedupState.empty.pure[F]
        case keys =>
          for
            ids <- redis.sUnion(keys*)
            now <- Time[F].timestamp
          yield DedupState(ids.map(id => IdRegistry(CommandId.unsafeFrom(id), now)))
      }

    def save(state: DedupState): F[Unit] =
      val key = s"${appId.name}-${appId.id.show}"
      redis.sAdd(key, state.ids.map(_.id.show).toSeq*) *>
        redis.expire(key, exp.value).void

  def fromClient[F[_]: MkRedis: MonadThrow: Time](
      client: RedisClient,
      appId: AppId,
      exp: KeyExpiration
  ): Resource[F, DedupRegistry[F]] =
    Redis[F].fromClient(client, RedisCodec.Utf8).map(from(_, appId, exp))

  def make[F[_]: Async: Log: MonadThrow: Time](
      uri: RedisURI,
      appId: AppId,
      exp: KeyExpiration
  ): Resource[F, DedupRegistry[F]] =
    RedisClient[F].from(uri.value).flatMap(fromClient[F](_, appId, exp))

