package trading.trace
package tracer

import trading.commands.*
import trading.events.*

import cats.effect.kernel.MonadCancelThrow
import cats.syntax.all.*
import io.circe.syntax.*
import natchez.{ EntryPoint, Kernel }

trait ForecastingTracer[F[_]]:
  def trace(cmd: ForecastCommand, evt: Either[AuthorEvent, ForecastEvent]): F[Unit]

object ForecastingTracer:
  def make[F[_]: MonadCancelThrow](
      ep: EntryPoint[F]
  ): ForecastingTracer[F] = new:
    def trace(cmd: ForecastCommand, evt: Either[AuthorEvent, ForecastEvent]): F[Unit] =
      ep.root("forecast-root").use { root =>
        root.span(s"forecast-command-${cmd.cid.show}").use { sp1 =>
          val cid        = evt.fold(_.cid, _.cid)
          val createdAt  = evt.fold(_.createdAt, _.createdAt)
          val durationMs = createdAt.value.toEpochMilli - cmd.createdAt.value.toEpochMilli
          val evtPayload = evt.fold(_.asJson, _.asJson)

          sp1.put(
            "correlation_id" -> cmd.cid.show,
            "created_at"     -> cmd.createdAt.show,
            "payload"        -> cmd.asJson.noSpaces
          ) *> sp1.span(s"forecast-event-${cid.show}").use { sp2 =>
            sp2.put(
              "correlation_id" -> cid.show,
              "created_at"     -> createdAt.show,
              "duration_tx_ms" -> durationMs.show,
              "payload"        -> evtPayload.noSpaces
            )
          }
        }
      }

    /* the following two methods are not used but this is how it could look if it followed the trading design */
    def command(cmd: ForecastCommand): F[Kernel] =
      ep.root("forecasting-root").use { root =>
        root.span(s"forecasting-command-${cmd.cid.show}").use { sp =>
          sp.put(
            "correlation_id" -> cmd.cid.show,
            "created_at"     -> cmd.createdAt.show,
            "payload"        -> cmd.asJson.noSpaces
          ) *> sp.kernel
        }
      }

    def event(kernel: Kernel, evt: Either[AuthorEvent, ForecastEvent]): F[Unit] =
      val cid       = evt.fold(_.cid, _.cid)
      val createdAt = evt.fold(_.createdAt, _.createdAt)
      val payload   = evt.fold(_.asJson, _.asJson)

      ep.continue(s"forecasting-command-${cid.show}", kernel).use { sp1 =>
        sp1.span(s"forecasting-event-${cid.show}").use { sp2 =>
          sp2.put(
            "correlation_id" -> cid.show,
            "created_at"     -> createdAt.show,
            "payload"        -> payload.noSpaces
          )
        }
      }
