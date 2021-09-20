package trading.alerts

import trading.core.EventSource
import trading.domain.Alert
import trading.events.TradeEvent
import trading.events.TradeEvent.CommandExecuted
import trading.lib.Producer
import trading.state.TradeState

import cats.Applicative
import cats.syntax.all._
import fs2.Pipe

trait AlertEngine[F[_]] {
  def run: Pipe[F, TradeEvent, Unit]
}

object AlertEngine {
  def make[F[_]: Applicative](
      producer: Producer[F, Alert]
  ): AlertEngine[F] =
    new AlertEngine[F] {
      val run: Pipe[F, TradeEvent, Unit] =
        _.evalMapAccumulate(TradeState.empty) { case (st, CommandExecuted(cmd, _)) =>
          val newSt = EventSource.runS(st)(cmd)
          val sendAlerts = (
            st.prices.get(cmd.symbol),
            newSt.prices.get(cmd.symbol)
          ).tupled.traverse_ { case (p, c) =>
            val previousAskMax = p.ask.keySet.max
            val previousBidMax = p.bid.keySet.max
            val currentAskMax  = c.ask.keySet.max
            val currentBidMax  = c.bid.keySet.max

            // dummy logic to simulate the trading market
            val alert =
              if (previousAskMax - currentAskMax > 0.3)
                Alert.StrongBuy(cmd.symbol, currentAskMax)
              else if (previousAskMax - currentAskMax > 0.2)
                Alert.Buy(cmd.symbol, currentAskMax)
              else if (currentBidMax - previousBidMax > 0.3)
                Alert.StrongSell(cmd.symbol, currentBidMax)
              else if (currentBidMax - previousBidMax > 0.2)
                Alert.Sell(cmd.symbol, currentBidMax)
              else
                Alert.Neutral(cmd.symbol, currentAskMax)

            producer.send(alert)
          }
          sendAlerts.tupleLeft(newSt)
        }.void
    }
}
