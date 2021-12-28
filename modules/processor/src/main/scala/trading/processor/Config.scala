package trading.processor

import java.util.UUID

import scala.concurrent.duration.*

import trading.domain.{ given, * }

import cats.effect.kernel.Async
import cats.syntax.all.*
import ciris.*
import com.comcast.ip4s.*
import dev.profunktor.pulsar.Config as PulsarConfig

final case class ProcessorConfig(
    httpPort: Port,
    pulsar: PulsarConfig,
    redisUri: RedisURI,
    keyExpiration: KeyExpiration,
    appId: AppId
)

object Config:
  def load[F[_]: Async]: F[ProcessorConfig] =
    Async[F].delay(UUID.randomUUID()).flatMap { uuid =>
      (
        env("HTTP_PORT").as[Port].default(port"9003"),
        env("PULSAR_URI").as[PulsarURI].fallback("pulsar://localhost:6650"),
        env("REDIS_URI").as[RedisURI].fallback("redis://localhost"),
        env("PROCESSOR_KEY_EXPIRATION").as[KeyExpiration].fallback(5.minutes).covary[F]
      ).parMapN { (port, pulsarUri, redisUri, exp) =>
        val pulsar =
          PulsarConfig.Builder
            .withTenant("public")
            .withNameSpace("default")
            .withURL(pulsarUri.value)
            .build
        ProcessorConfig(port, pulsar, redisUri, exp, AppId("processor", uuid))
      }.load[F]
    }
