package trading.feed

import trading.domain.{ given, * }

import cats.effect.kernel.Async
import cats.syntax.all.*
import ciris.*
import com.comcast.ip4s.*
import dev.profunktor.pulsar.Config as PulsarConfig

final case class FeedConfig(
    httpPort: Port,
    pulsar: PulsarConfig
)

object Config:
  def load[F[_]: Async]: F[FeedConfig] =
    (
      env("HTTP_PORT").as[Port].default(port"9001"),
      env("PULSAR_URI").as[PulsarURI].fallback("pulsar://localhost:6650").covary[F]
    ).parMapN { (port, pulsarUri) =>
      val pulsar =
        PulsarConfig.Builder
          .withTenant("public")
          .withNameSpace("default")
          .withURL(pulsarUri.value)
          .build
      FeedConfig(port, pulsar)
    }.load[F]
