package trading.alerts

import scala.concurrent.duration.*

import cats.effect.kernel.Async
import cats.syntax.all.*
import ciris.*
import dev.profunktor.pulsar.Config as PulsarConfig

import trading.domain.*

final case class AlertsConfig(
    pulsar: PulsarConfig,
    redisUri: RedisURI
)

object Config:
  //extension [F[_]](cv: ConfigValue[F, A])
  //def fallback(value: A): ConfigValue[F, Type] =
  //cv.default(value)

  def load[F[_]: Async]: F[AlertsConfig] =
    (
      env("PULSAR_URI").as[PulsarURI].default(PulsarURI("pulsar://localhost:6650")),
      env("REDIS_URI").as[RedisURI].default(RedisURI("redis://localhost"))
    ).parMapN { (pulsarUri, redisUri) =>
      val pulsar =
        PulsarConfig.Builder
          .withTenant("public")
          .withNameSpace("default")
          .withURL(pulsarUri.value)
          .build
      AlertsConfig(pulsar, redisUri)
    }.load[F]
