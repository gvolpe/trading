package trading.ws

import cats.effect.std.Console
import org.http4s.server.Server
import org.http4s.server.defaults.Banner

object Ember:
  def showBanner[F[_]](using C: Console[F])(s: Server): F[Unit] =
    C.println(s"\n${Banner.mkString("\n")}\nHTTP Server started at ${s.address}")
