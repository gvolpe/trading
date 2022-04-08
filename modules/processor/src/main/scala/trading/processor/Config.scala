package trading.processor

import java.util.UUID

import trading.domain.{ given, * }
import trading.lib.GenUUID

import cats.effect.kernel.Async
import cats.syntax.all.*
import ciris.*
import com.comcast.ip4s.*
import dev.profunktor.pulsar.Config as PulsarConfig

final case class ProcessorConfig(
    httpPort: Port,
    pulsar: PulsarConfig,
    appId: AppId
)

object Config:
  def load[F[_]: Async]: F[ProcessorConfig] =
    GenUUID[F].make[UUID].flatMap { uuid =>
      (
        env("HTTP_PORT").as[Port].default(port"9003"),
        env("PULSAR_URI").as[PulsarURI].fallback("pulsar://localhost:6650").covary[F]
      ).parMapN { (port, pulsarUri) =>
        val pulsar =
          PulsarConfig.Builder
            .withTenant("public")
            .withNameSpace("default")
            .withURL(pulsarUri.value)
            .build
        ProcessorConfig(port, pulsar, AppId("processor", uuid))
      }.load[F]
    }
