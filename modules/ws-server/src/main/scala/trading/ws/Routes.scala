package trading.ws

import trading.domain.Alert
import trading.lib.GenUUID

import cats.effect.kernel.Concurrent
import cats.effect.std.Console
import cats.syntax.all._
import fs2.concurrent.Topic
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder

final case class Routes[F[_]: Concurrent: Console: GenUUID](
    topic: Topic[F, Alert]
) extends Http4sDsl[F] {

  val routes: HttpRoutes[F] = HttpRoutes.of {
    case GET -> Root / "health" =>
      Ok("WS Server up")

    case GET -> Root / "ws" =>
      Handler.make[F](topic).flatMap { h =>
        WebSocketBuilder[F].build(h.send, h.receive)
      }
  }

}
