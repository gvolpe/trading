package trading.ws

import trading.domain.Alert
import trading.lib.{ GenUUID, Logger }

import cats.effect.kernel.Concurrent
import cats.syntax.all.*
import fs2.concurrent.Topic
import org.http4s.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder

final class WsRoutes[F[_]: Concurrent: GenUUID: Logger](
    ws: WebSocketBuilder[F],
    topic: Topic[F, Alert]
) extends Http4sDsl[F]:

  // format: off
  val routes: HttpRoutes[F] = HttpRoutes.of {
    case GET -> Root / "v1" / "ws" =>
      Handler.make[F](topic).flatMap { h =>
        ws.build(h.send, h.receive)
      }
  }
