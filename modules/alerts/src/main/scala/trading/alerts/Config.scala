package trading.alerts

import java.util.UUID

import trading.domain.{ *, given }
import trading.lib.GenUUID

import cats.effect.kernel.Async
import cats.syntax.all.*
import ciris.*
import com.comcast.ip4s.*
import dev.profunktor.pulsar.Config as PulsarConfig

final case class AlertsConfig(
    httpPort: Port,
    pulsar: PulsarConfig,
    redisUri: RedisURI,
    appId: AppId
)

object Config:
  def load[F[_]: Async]: F[AlertsConfig] =
    GenUUID[F].make[UUID].flatMap { uuid =>
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
        AlertsConfig(port, pulsar, redisUri, AppId("alerts", uuid))
      }.load[F]
    }
