package trading.lib

import cats.Applicative
import cats.effect.kernel.Sync
import dev.profunktor.pulsar.Topic
import dev.profunktor.redis4cats.effect.Log
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
          msg.copy(position = msg.position.copy(enclosureName = "trading.lib.Logger", line = -1))
        }
    }

  given redisLog[F[_]](using L: Logger[F]): Log[F] = new:
    def debug(msg: => String): F[Unit] = L.debug(msg)
    def error(msg: => String): F[Unit] = L.error(msg)
    def info(msg: => String): F[Unit]  = L.info(msg)

  object NoOp:
    given [F[_]: Applicative]: Logger[F] with
      def info(str: => String): F[Unit]  = Applicative[F].unit
      def error(str: => String): F[Unit] = info(str)
      def debug(str: => String): F[Unit] = info(str)
      def warn(str: => String): F[Unit]  = info(str)
