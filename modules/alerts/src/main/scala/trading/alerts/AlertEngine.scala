package trading.alerts

import trading.commands.TradeCommand
import trading.domain.{ Alert, TradeAction }
import trading.events.TradeEvent
import trading.events.TradeEvent.CommandExecuted
import trading.lib.Producer

import cats.Applicative
import cats.syntax.all._

trait AlertEngine[F[_]] {
  def run: TradeEvent => F[Unit]
}

object AlertEngine {
  def make[F[_]: Applicative](
      producer: Producer[F, Alert]
  ): AlertEngine[F] =
    new AlertEngine[F] {
      val run: TradeEvent => F[Unit] = {
        // TODO: add logic based on some alert state
        case CommandExecuted(TradeCommand.Create(symbol, TradeAction.Ask, price, _, _, _), _) =>
          producer.send(Alert.StrongBuy(symbol, price))
        case _ => ().pure[F]
      }
    }
}
