package trading.core.http

import cats.Monad
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

final class HealthRoutes[F[_]: Monad] extends Http4sDsl[F]:

  // format: off
  val routes: HttpRoutes[F] = HttpRoutes.of {
    case GET -> Root / "health" => Ok()
  }
