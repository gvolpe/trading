package trading.lib

import cats.Applicative
import cats.effect.std.Console
import dev.profunktor.pulsar.Topic
import io.circe.{ Encoder, Json }
import io.circe.syntax.*

trait Logger[F[_]]:
  def info(str: => String): F[Unit]
  def error(str: => String): F[Unit]
  def warn(str: => String): F[Unit]

object Logger:
  def apply[F[_]: Logger]: Logger[F] = summon

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

  object NoOp:
    given [F[_]: Applicative]: Logger[F] with
      def info(str: => String): F[Unit]  = Applicative[F].unit
      def error(str: => String): F[Unit] = Applicative[F].unit
      def warn(str: => String): F[Unit]  = Applicative[F].unit

  given [F[_]: Console]: Logger[F] with
    def info(str: => String): F[Unit]  = Console[F].println(s"[info] - $str")
    def error(str: => String): F[Unit] = Console[F].errorln(s"[error] - $str")
    def warn(str: => String): F[Unit]  = Console[F].println(s"[warn] - $str")
