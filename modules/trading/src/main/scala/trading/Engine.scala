package trading

import trading.commands.TradeCommand
import trading.core.{ Producer, Time }
import trading.domain.TradeAction
import trading.events.TradeEvent
import trading.state.TradeState

import cats.effect.kernel.Concurrent
import cats.syntax.all._
import fs2.Stream
import trading.core.StateCache

trait Engine[F[_]] {
  def run(state: TradeState)(commands: Stream[F, TradeCommand]): F[Unit]
}

object Engine {
  def make[F[_]: Concurrent: Time](
      producer: Producer[F, TradeEvent],
      cache: StateCache[F]
  ): Engine[F] =
    new Engine[F] {
      def run(state: TradeState)(commands: Stream[F, TradeCommand]): F[Unit] =
        commands
          .evalMapAccumulate(state) {
            case (st, cmd @ TradeCommand.Create(symbol, TradeAction.Ask, price, _, _, _)) =>
              //TODO: use lenses
              val prices = st.prices.updatedWith(symbol) {
                case Some((_, bp)) => Some(price -> bp)
                case None          => Some(price -> BigDecimal(0.0))
              }
              Time[F].timestamp.flatMap { ts =>
                val event = TradeEvent.CommandExecuted(cmd, ts)
                val newSt = TradeState(prices)
                (producer.send(event) >> cache.save(newSt)).tupleLeft(newSt)
              }
            case (st, cmd @ TradeCommand.Create(symbol, TradeAction.Bid, price, _, _, _)) =>
              (st -> ()).pure[F]
            case (st, cmd: TradeCommand.Update) => (st -> ()).pure[F]
            case (st, cmd: TradeCommand.Delete) => (st -> ()).pure[F]
          }
          .compile
          .drain
    }
}
