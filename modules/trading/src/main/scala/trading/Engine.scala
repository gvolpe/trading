package trading

import trading.commands.TradeCommand
import trading.core.{ Producer, Time }
import trading.domain.TradeAction
import trading.events.TradeEvent
import trading.state.{ Prices, TradeState }

import cats.effect.kernel.Concurrent
import cats.syntax.all._
import fs2.Stream

trait Engine[F[_]] {
  def run(state: TradeState)(commands: Stream[F, TradeCommand]): F[TradeState]
}

object Engine {
  def make[F[_]: Concurrent: Time](
      producer: Producer[F, TradeEvent]
  ): Engine[F] =
    new Engine[F] {
      def run(state: TradeState)(commands: Stream[F, TradeCommand]): F[TradeState] =
        commands
          .evalMapAccumulate(state) {
            case (st, cmd @ TradeCommand.Create(symbol, TradeAction.Ask, pl, price, _, _, _)) =>
              //TODO: use lenses
              val prices = st.prices.updatedWith(symbol) {
                case Some(p) => Some(p.copy(ask = p.ask.updated(pl, price)))
                case None    => Some(Prices(ask = Map(pl -> price), bid = Map.empty))
              }
              Time[F].timestamp.flatMap { ts =>
                val event = TradeEvent.CommandExecuted(cmd, ts)
                producer.send(event).tupleLeft(TradeState(prices))
              }
            case (st, cmd @ TradeCommand.Create(symbol, TradeAction.Bid, pl, price, _, _, _)) =>
              (st -> ()).pure[F]
            case (st, cmd: TradeCommand.Update) => (st -> ()).pure[F]
            case (st, cmd: TradeCommand.Delete) => (st -> ()).pure[F]
          }
          .map(_._1)
          .compile
          .lastOrError
    }
}
