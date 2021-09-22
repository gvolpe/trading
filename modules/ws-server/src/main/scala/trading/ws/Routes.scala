package trading.ws

import trading.domain.Alert

import cats.Monad
import cats.effect.std.Console
import cats.syntax.all._
import fs2.{ Pipe, Stream }
import io.circe.parser.{ decode => jsonDecode }
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{ Close, Text }

final case class Routes[F[_]: Console: Monad](
    alerts: Stream[F, Alert]
) extends Http4sDsl[F] {

  val encode: WsOut => WebSocketFrame =
    out => Text(out.asJson.noSpaces)

  val decode: WebSocketFrame => Either[String, WsIn] = {
    case Close(_)     => "WS Close".asLeft
    case Text(msg, _) => jsonDecode[WsIn](msg).leftMap(_.getMessage)
    case e            => s"Unexpected WS message: $e".asLeft
  }

  val routes: HttpRoutes[F] = HttpRoutes.of {
    case GET -> Root / "health" =>
      Ok("WS Server up")

    case GET -> Root / "ws" =>
      val send: Stream[F, WebSocketFrame] =
        alerts.map(x => encode(x.wsOut))

      val receive: Pipe[F, WebSocketFrame, Unit] =
        _.evalMap {
          decode(_) match {
            case Left(e) =>
              Console[F].error(s">>> Unexpected WS message: $e")
            case Right(in) =>
              Console[F].println(s">>> WSIn: $in")
          }
        }

      WebSocketBuilder[F].build(send, receive)
  }

}
