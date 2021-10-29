package trading.alerts

import trading.domain.{*, given}

import cats.effect.kernel.Async
import cats.syntax.all.*
import ciris.*
import com.comcast.ip4s.*
import dev.profunktor.pulsar.Config as PulsarConfig

final case class AlertsConfig(
    httpPort: Port,
    pulsar: PulsarConfig,
    redisUri: RedisURI
)

object Config:
  def load[F[_]: Async]: F[AlertsConfig] =
    (
      env("HTTP_PORT").as[Port].default(port"9004"),
      env("PULSAR_URI").as[PulsarURI].fallback("pulsar://localhost:6650"),
      env("REDIS_URI").as[RedisURI].fallback("redis://localhost").covary[F]
    ).parMapN { (port, pulsarUri, redisUri) =>
      val pulsar =
        PulsarConfig.Builder
          .withTenant("public")
          .withNameSpace("default")
          .withURL(pulsarUri.value)
          .build
      AlertsConfig(port, pulsar, redisUri)
    }.load[F]
