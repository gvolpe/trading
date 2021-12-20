package trading.lib

import cats.Applicative
import cats.effect.IO
import cats.effect.kernel.Sync
import cats.effect.std.Console
import dev.profunktor.pulsar.Topic
import io.circe.{ Encoder, Json }
import io.circe.syntax.*
import io.odin.{ Logger as OdinLogger, consoleLogger }
import io.odin.formatter.Formatter
import io.odin.meta.Position
import io.odin.syntax.*

trait Logger[F[_]]:
  def info(str: => String): F[Unit]
  def error(str: => String): F[Unit]
  def debug(str: => String): F[Unit]
  def warn(str: => String): F[Unit]

object Logger:
  def apply[F[_]: Logger]: Logger[F] = summon

  def from[F[_]](log: OdinLogger[F]): Logger[F] = new:
    def info(str: => String): F[Unit]  = log.info(str)
    def error(str: => String): F[Unit] = log.error(str)
    def debug(str: => String): F[Unit] = log.debug(str)
    def warn(str: => String): F[Unit]  = log.warn(str)

  // format: off
  def pulsar[F[_]: Logger, A: Encoder](
      flow: "in" | "out"
  ): A => Topic.URL => F[Unit] =
    p => t => Logger[F].info {
      Json.obj(
        "flow"    -> flow.asJson,
        "payload" -> p.asJson,
        "topic"   -> t.value.asJson
      )
      .noSpaces
    }
  // format: on

  given [F[_]: Sync]: Logger[F] =
    from[F] {
      consoleLogger[F](Formatter.colorful)
        .contramap { msg =>
          msg.copy(position = msg.position.copy(enclosureName = "trading.lib.Logger", line = 0))
        }
    }

  object NoOp:
    given [F[_]: Applicative]: Logger[F] with
      def info(str: => String): F[Unit]  = Applicative[F].unit
      def error(str: => String): F[Unit] = info(str)
      def debug(str: => String): F[Unit] = info(str)
      def warn(str: => String): F[Unit]  = info(str)

  object StdOut:
    given [F[_]: Console]: Logger[F] with
      def info(str: => String): F[Unit]  = Console[F].println(s"[info] - $str")
      def error(str: => String): F[Unit] = Console[F].errorln(s"[error] - $str")
      def debug(str: => String): F[Unit] = Console[F].errorln(s"[debug] - $str")
      def warn(str: => String): F[Unit]  = Console[F].println(s"[warn] - $str")
