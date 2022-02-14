package trading.trace

import trading.commands.*
import trading.domain.Alert
import trading.events.*

import cats.effect.kernel.MonadCancelThrow
import cats.syntax.all.*
import io.circe.syntax.*
import natchez.{ EntryPoint, Kernel }

trait Tracer[F[_]]:
  def trading1(cmd: TradeCommand, evt: TradeEvent): F[Kernel]
  def trading2(kernel: Kernel, evt: TradeEvent, alt: Alert): F[Unit]
  def forecasting(cmd: ForecastCommand, evt: Either[AuthorEvent, ForecastEvent]): F[Unit]

object Tracer:
  def make[F[_]: MonadCancelThrow](
      ep: EntryPoint[F]
  ): Tracer[F] = new:
    def trading1(cmd: TradeCommand, evt: TradeEvent): F[Kernel] =
      ep.root("trading-root").use { root =>
        val durationMs = evt.createdAt.value.toEpochMilli - cmd.createdAt.value.toEpochMilli
        // Command span
        root.span(s"trading-command-${cmd.cid.show}").use { sp1 =>
          sp1.put("correlation_id" -> cmd.cid.show) *>
            sp1.put("created_at"   -> cmd.createdAt.show) *>
            sp1.put("payload" -> cmd.asJson.noSpaces) *>
            // Event span
            sp1.span(s"trading-event-${evt.cid.show}").use { sp2 =>
              sp2.put("correlation_id" -> evt.cid.show) *>
                sp2.put("created_at"   -> evt.createdAt.show) *>
                sp2.put("duration_tx_ms" -> durationMs.show) *>
                sp2.put("payload" -> evt.asJson.noSpaces) *>
                // Alerts span
                sp2.span(s"trading-alerts-${evt.cid.show}").use(_.kernel)
            }
        }
      }

    def trading2(kernel: Kernel, evt: TradeEvent, alt: Alert): F[Unit] =
      ep.continue(s"trading-alerts-${alt.cid.show}", kernel).use { sp1 =>
        val durationMs = alt.createdAt.value.toEpochMilli - evt.createdAt.value.toEpochMilli

        val spanName = alt match
          case a: Alert.TradeAlert  => s"alt-${a.alertType.show}-${alt.id.show}"
          case a: Alert.TradeUpdate => s"alt-${a.status.show}-${alt.id.show}"

        sp1.span(spanName).use { sp2 =>
          sp2.put("correlation_id" -> alt.cid.show) *>
            sp2.put("created_at"   -> alt.createdAt.show) *>
            sp2.put("duration_tx_ms" -> durationMs.show) *>
            sp2.put("payload" -> alt.asJson.noSpaces)
        }
      }

    def forecasting(cmd: ForecastCommand, evt: Either[AuthorEvent, ForecastEvent]): F[Unit] =
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
