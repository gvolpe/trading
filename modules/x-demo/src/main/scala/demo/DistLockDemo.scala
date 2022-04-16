package demo

import scala.concurrent.duration.*

import cats.effect.*
import cats.syntax.all.*
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.effects.SetArg.Existence.Nx
import dev.profunktor.redis4cats.effects.SetArg.Ttl.Px
import dev.profunktor.redis4cats.effects.SetArgs

val redis: RedisCommands[IO, String, String] = ???

type Lock = String

val lockName = "my_lock"
val clientId = "porcupine-ea4190d4-0807-4c90-aea9-41c19e249c84"

val acquireLock: IO[Lock] =
  redis
    .set(lockName, clientId, SetArgs(Nx, Px(30000.millis)))
    .flatMap {
      case true  => IO.unit
      case false => IO.sleep(50.millis) >> acquireLock
    }
    .as(clientId)

val deleteLock: Lock => IO[Unit] =
  id =>
    redis.get(lockName).flatMap {
      _.traverse_ { v =>
        redis.del(lockName).whenA(v === id)
      }
    }

val lock: Resource[IO, Lock] =
  Resource.make(acquireLock)(deleteLock)

val program: IO[Unit] =
  lock.surround {
    IO.println("perform computation")
  }
