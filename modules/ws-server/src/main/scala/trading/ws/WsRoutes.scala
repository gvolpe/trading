package trading.ws

import trading.domain.SocketId
import trading.lib.{ GenUUID, Logger }

import cats.Monad
import cats.syntax.all.*
import org.http4s.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder

final class WsRoutes[F[_]: GenUUID: Logger: Monad](
    ws: WebSocketBuilder[F],
    mkHandler: SocketId => F[Handler[F]]
) extends Http4sDsl[F]:

  // format: off
  val routes: HttpRoutes[F] = HttpRoutes.of {
    case GET -> Root / "v1" / "ws" =>
      GenUUID[F].make[SocketId]
        .flatMap(mkHandler)
        .flatMap(h => ws.build(h.send, h.receive))
  }
