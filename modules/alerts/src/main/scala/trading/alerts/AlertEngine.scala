package trading.alerts

import trading.core.EventSource
import trading.domain._
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
        // TODO: Could read snapshots instead of starting out empty
        _.evalMapAccumulate(TradeState.empty) { case (st, CommandExecuted(cmd, _)) =>
          val nst = EventSource.runS(st)(cmd)
          val p   = st.prices.get(cmd.symbol)
          val c   = nst.prices.get(cmd.symbol)

          //println(s">>> ST: $st")
          //println(s">>> NST: $nst \n")

          val previousAskMax: AskPrice = p.flatMap(_.ask.keySet.maxOption).getOrElse(0.0)
          val previousBidMax: BidPrice = p.flatMap(_.bid.keySet.maxOption).getOrElse(0.0)
          val currentAskMax: AskPrice  = c.flatMap(_.ask.keySet.maxOption).getOrElse(0.0)
          val currentBidMax: BidPrice  = c.flatMap(_.bid.keySet.maxOption).getOrElse(0.0)

          // dummy logic to simulate the trading market
          val alert: Option[Alert] =
            if (previousAskMax - currentAskMax > 0.3)
              Alert.StrongBuy(cmd.symbol, currentAskMax).some
            else if (previousAskMax - currentAskMax > 0.2)
              Alert.Buy(cmd.symbol, currentAskMax).some
            else if (currentBidMax - previousBidMax > 0.3)
              Alert.StrongSell(cmd.symbol, currentBidMax).some
            else if (currentBidMax - previousBidMax > 0.2)
              Alert.Sell(cmd.symbol, currentBidMax).some
            else
              none[Alert]
          //Alert.Neutral(cmd.symbol, currentAskMax)

          alert.traverse_(producer.send).tupleLeft(nst)
        }.void
    }
}
