package trading.lib

import cats.effect.std.Console

trait Logger[F[_]]:
  def info(str: => String): F[Unit]
  def error(str: => String): F[Unit]
  def warn(str: => String): F[Unit]

object Logger:
  def apply[F[_]](using ev: Logger[F]): Logger[F] = ev

  given forConsole[F[_]](using C: Console[F]): Logger[F] =
    new Logger[F] {
      def info(str: => String): F[Unit]  = C.println(s"[info] - $str")
      def error(str: => String): F[Unit] = C.errorln(s"[error] - $str")
      def warn(str: => String): F[Unit]  = C.println(s"[warn] - $str")
    }
