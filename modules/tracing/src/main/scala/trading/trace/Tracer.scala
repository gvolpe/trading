package trading.trace

import trading.commands.*
import trading.domain.Alert
import trading.events.*

import cats.effect.kernel.MonadCancelThrow
import cats.syntax.all.*
import io.circe.syntax.*
import natchez.EntryPoint

trait Tracer[F[_]]:
  def trading(cmd: TradeCommand, evt: TradeEvent, alt: Alert): F[Unit]
  def forecasting(evt: Either[AuthorEvent, ForecastEvent], cmd: ForecastCommand): F[Unit]

object Tracer:
  def make[F[_]: MonadCancelThrow](
      ep: EntryPoint[F]
  ): Tracer[F] = new:
    def trading(cmd: TradeCommand, evt: TradeEvent, alt: Alert): F[Unit] =
      ep.root("trading-root").use { root =>
        val evtDurationMs = evt.createdAt.value.toEpochMilli - cmd.createdAt.value.toEpochMilli
        val altDurationMs = alt.createdAt.value.toEpochMilli - cmd.createdAt.value.toEpochMilli
        // Command span
        root.span(s"trading-command-${cmd.cid.show}").use { sp1 =>
          sp1.put("correlation_id" -> cmd.cid.show) *>
            sp1.put("created_at"   -> cmd.createdAt.show) *>
            sp1.put("payload" -> cmd.asJson.noSpaces) *>
            // Event span
            sp1.span(s"trading-event-${evt.cid.show}").use { sp2 =>
              sp2.put("correlation_id" -> evt.cid.show) *>
                sp2.put("created_at"   -> evt.createdAt.show) *>
                sp2.put("duration_tx_ms" -> evtDurationMs.show) *>
                sp2.put("payload" -> evt.asJson.noSpaces) *>
                // Alert span
                sp2.span(s"trading-alert-${alt.cid.show}").use { sp3 =>
                  sp3.put("correlation_id" -> alt.cid.show) *>
                    sp3.put("created_at"   -> alt.createdAt.show) *>
                    sp3.put("duration_tx_ms" -> altDurationMs.show) *>
                    sp3.put("payload" -> alt.asJson.noSpaces)
                }
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
