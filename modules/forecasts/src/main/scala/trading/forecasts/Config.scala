package trading.forecasts

import scala.concurrent.duration.*

import trading.Newtype
import trading.domain.{ given, * }

import cats.effect.kernel.Async
import cats.syntax.all.*
import ciris.*
import com.comcast.ip4s.*
import dev.profunktor.pulsar.Config as PulsarConfig

final case class ForecastsConfig(
    httpPort: Port,
    pulsar: PulsarConfig,
    redisUri: RedisURI,
    authorExp: Config.AuthorExpiration,
    forecastExp: Config.ForecastExpiration
)

object Config:
  type AuthorExpiration = AuthorExpiration.Type
  object AuthorExpiration extends Newtype[FiniteDuration]

  type ForecastExpiration = ForecastExpiration.Type
  object ForecastExpiration extends Newtype[FiniteDuration]

  def load[F[_]: Async]: F[ForecastsConfig] =
    (
      env("HTTP_PORT").as[Port].default(port"9006"),
      env("PULSAR_URI").as[PulsarURI].fallback("pulsar://localhost:6650"),
      env("REDIS_URI").as[RedisURI].fallback("redis://localhost"),
      env("AUTHOR_EXPIRATION").as[AuthorExpiration].fallback(90.days),
      env("FORECAST_EXPIRATION").as[ForecastExpiration].fallback(90.days).covary[F]
    ).parMapN { (port, pulsarUri, redisUri, authorExp, fcExp) =>
      val pulsar =
        PulsarConfig.Builder
          .withTenant("public")
          .withNameSpace("default")
          .withURL(pulsarUri.value)
          .build
      ForecastsConfig(port, pulsar, redisUri, authorExp, fcExp)
    }.load[F]
