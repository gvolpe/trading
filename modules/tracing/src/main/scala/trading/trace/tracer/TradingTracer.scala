package trading.trace
package tracer

import trading.commands.*
import trading.domain.Alert
import trading.events.*

import cats.effect.kernel.MonadCancelThrow
import cats.syntax.all.*
import io.circe.syntax.*
import natchez.{ EntryPoint, Kernel }

trait TradingTracer[F[_]]:
  def command(cmd: TradeCommand): F[CmdKernel]
  def event(kernel: CmdKernel, evt: TradeEvent): F[EvtKernel]
  def alert(kernel: EvtKernel, alt: Alert): F[Unit]

object TradingTracer:
  def make[F[_]: MonadCancelThrow](
      ep: EntryPoint[F]
  ): TradingTracer[F] = new:
    def command(cmd: TradeCommand): F[CmdKernel] =
      ep.root("trading-root").use { root =>
        root.span(s"trading-command-${cmd.cid.show}").use { sp =>
          sp.put(
            "correlation_id" -> cmd.cid.show,
            "created_at"     -> cmd.createdAt.show,
            "payload"        -> cmd.asJson.noSpaces
          ) *> sp.kernel.map(CmdKernel(_))
        }
      }

    def event(kernel: CmdKernel, evt: TradeEvent): F[EvtKernel] =
      ep.continue(s"trading-command-${evt.cid.show}", kernel.value).use { sp1 =>
        sp1.span(s"trading-event-${evt.cid.show}").use { sp2 =>
          sp2.put(
            "correlation_id" -> evt.cid.show,
            "created_at"     -> evt.createdAt.show,
            "payload"        -> evt.asJson.noSpaces
          ) *> sp2.kernel.map(EvtKernel(_))
        }
      }

    def alert(kernel: EvtKernel, alt: Alert): F[Unit] =
      ep.continue(s"trading-event-${alt.cid.show}", kernel.value).use { sp1 =>
        sp1.span(s"trading-alert-${alt.cid.show}").use { sp2 =>
          sp2.put(
            "correlation_id" -> alt.cid.show,
            "created_at"     -> alt.createdAt.show,
            "payload"        -> alt.asJson.noSpaces
          )
        }
      }
