package trading.lib

import scala.concurrent.duration.*

import trading.domain.AppId
import trading.lib.Logger

import cats.effect.kernel.{ Resource, Temporal }
import cats.syntax.all.*
import dev.profunktor.redis4cats.{ Redis, RedisCommands }
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.MkRedis
import dev.profunktor.redis4cats.effects.SetArg.Existence.Nx
import dev.profunktor.redis4cats.effects.SetArg.Ttl.Px
import dev.profunktor.redis4cats.effects.SetArgs

trait DistLock[F[_]]:
  def refresh: F[Unit]

object DistLock:
  def make[F[_]: Logger: MkRedis: Temporal](
      lockName: String,
      appId: AppId,
      client: RedisClient
  ): Resource[F, DistLock[F]] =
    Redis[F].fromClient(client, RedisCodec.Utf8).flatMap(from(lockName, appId, _))

  def from[F[_]: Logger: Temporal](
      lockName: String,
      appId: AppId,
      redis: RedisCommands[F, String, String]
  ): Resource[F, DistLock[F]] =
    def acquireLock: F[Unit] =
      redis
        .set(lockName, appId.show, SetArgs(Nx, Px(10000.millis))) // 10 seconds
        .flatMap {
          case true  => Logger[F].debug(s"GOT LOCK: ${appId.show}")
          case false => Temporal[F].sleep(1500.millis) >> acquireLock
        }

    val deleteLock: F[Unit] =
      redis.get(lockName).flatMap {
        _.traverse_ { v =>
          val log = Logger[F].debug(s"DELETE LOCK: ${appId.show}")
          (log *> redis.del(lockName)).whenA(v === appId.show)
        }
      }

    Resource.make(acquireLock)(_ => deleteLock).as {
      new:
        def refresh: F[Unit] =
          Logger[F].debug(s"REFRESH LOCK: ${appId.show}") <*
            redis.expire(lockName, 10.seconds)
    }
