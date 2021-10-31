package trading.trace

import trading.commands.*
import trading.events.*

import cats.effect.kernel.MonadCancelThrow
import cats.syntax.all.*
import io.circe.syntax.*
import natchez.EntryPoint

trait Tracer[F[_]]:
  def trading(evt: TradeEvent, cmd: TradeCommand): F[Unit]
  def forecasting(evt: Either[AuthorEvent, ForecastEvent], cmd: ForecastCommand): F[Unit]

object Tracer:
  def make[F[_]: MonadCancelThrow](
      ep: EntryPoint[F]
  ): Tracer[F] = new:
    def trading(evt: TradeEvent, cmd: TradeCommand): F[Unit] =
      ep.root("trading-root").use { root =>
        root.span(s"trading-command-${cmd.cid.show}").use { sp1 =>
          val durationMs = evt.createdAt.value.toEpochMilli - cmd.createdAt.value.toEpochMilli
          sp1.put("correlation_id" -> cmd.cid.show) *>
            sp1.put("created_at"   -> cmd.createdAt.show) *>
            sp1.put("payload" -> cmd.asJson.noSpaces) *>
            sp1.span(s"trading-event-${evt.cid.show}").use { sp2 =>
              sp2.put("correlation_id" -> evt.cid.show) *>
                sp2.put("created_at"   -> evt.createdAt.show) *>
                sp1.put("duration_tx_ms" -> durationMs.show) *>
                sp1.put("payload" -> evt.asJson.noSpaces)
            }
        }
      }

    def forecasting(evt: Either[AuthorEvent, ForecastEvent], cmd: ForecastCommand): F[Unit] =
      ep.root("forecast-root").use { root =>
        root.span(s"forecast-command-${cmd.cid.show}").use { sp1 =>
          val cid        = evt.fold(_.cid, _.cid)
          val createdAt  = evt.fold(_.createdAt, _.createdAt)
          val durationMs = createdAt.value.toEpochMilli - cmd.createdAt.value.toEpochMilli
          val evtPayload = evt.fold(_.asJson, _.asJson)

          sp1.put("correlation_id" -> cmd.cid.show) *>
            sp1.put("created_at"   -> cmd.createdAt.show) *>
            sp1.put("payload" -> cmd.asJson.noSpaces) *>
            sp1.span(s"forecast-event-${cid.show}").use { sp2 =>
              sp2.put("correlation_id" -> cid.show) *>
                sp2.put("created_at"   -> createdAt.show) *>
                sp2.put("duration_tx_ms" -> durationMs.show) *>
                sp2.put("payload" -> evtPayload.noSpaces)
            }
        }
      }
