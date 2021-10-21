package trading.snapshots

import scala.concurrent.duration.*

import cats.effect.kernel.Async
import cats.syntax.all.*
import ciris.*
import dev.profunktor.pulsar.Config as PulsarConfig

import trading.domain.*

final case class SnapshotsConfig(
    pulsar: PulsarConfig,
    redisUri: RedisURI,
    keyExpiration: KeyExpiration
)

object Config:
  def load[F[_]: Async]: F[SnapshotsConfig] =
    (
      env("PULSAR_URI").as[PulsarURI].fallback("pulsar://localhost:6650"),
      env("REDIS_URI").as[RedisURI].fallback("redis://localhost"),
      env("SNAPSHOT_KEY_EXPIRATION").as[KeyExpiration].fallback(1.hour).covary[F]
    ).parMapN { (pulsarUri, redisUri, keyExp) =>
      val pulsar =
        PulsarConfig.Builder
          .withTenant("public")
          .withNameSpace("default")
          .withURL(pulsarUri.value)
          .build
      SnapshotsConfig(pulsar, redisUri, keyExp)
    }.load[F]
