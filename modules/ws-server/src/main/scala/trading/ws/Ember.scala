package trading.ws

import cats.effect.std.Console
import org.http4s.server.Server
import org.http4s.server.defaults.Banner

object Ember {
  def showBanner[F[_]: Console](s: Server): F[Unit] =
    Console[F].println(s"\n${Banner.mkString("\n")}\nHTTP Server started at ${s.address}")
}
