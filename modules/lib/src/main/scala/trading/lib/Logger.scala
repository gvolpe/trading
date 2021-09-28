package trading.lib

import cats.effect.std.Console

trait Logger[F[_]] {
  def info(str: => String): F[Unit]
  def error(str: => String): F[Unit]
}

object Logger {
  @inline def apply[F[_]: Logger]: Logger[F] = implicitly

  implicit def forConsole[F[_]: Console]: Logger[F] =
    new Logger[F] {
      def error(str: => String): F[Unit] = Console[F].errorln(str)
      def info(str: => String): F[Unit]  = Console[F].println(str)
    }
}
