package trading.core.http

import cats.effect.kernel.{ Async, Resource }
import cats.effect.std.Console
import cats.syntax.all.*
import com.comcast.ip4s.*
import org.http4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.Server
import org.http4s.server.defaults.Banner
import org.http4s.server.websocket.WebSocketBuilder

object Ember:
  private def showBanner[F[_]](using C: Console[F])(s: Server): F[Unit] =
    C.println(s"\n${Banner.mkString("\n")}\nHTTP Server started at ${s.address}")

  private def make[F[_]: Async] =
    EmberServerBuilder
      .default[F]
      .withHost(host"0.0.0.0")
      .withPort(port"9000")

  def websocket[F[_]: Async: Console](
      f: WebSocketBuilder[F] => HttpRoutes[F]
  ): Resource[F, Server] =
    make[F]
      .withHttpWebSocketApp { ws =>
        (f(ws) <+> HealthRoutes[F]().routes).orNotFound
      }
      .build
      .evalTap(showBanner[F])

  def default[F[_]: Async: Console]: Resource[F, Server] =
    make[F]
      .withHttpApp(HealthRoutes[F]().routes.orNotFound)
      .build
      .evalTap(showBanner[F])
