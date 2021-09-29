package trading.alerts

import trading.core.snapshots.SnapshotReader
import trading.core.{ Conflicts, EventSource }
import trading.domain.AlertType._
import trading.domain._
import trading.events.TradeEvent
import trading.events.TradeEvent.CommandExecuted
import trading.lib.{ Logger, Producer, Time }
import trading.state.{ DedupState, TradeState }

import cats.Monad
import cats.syntax.all._
import fs2.{ Pipe, Stream }

trait AlertEngine[F[_]] {
  def run: Pipe[F, TradeEvent, Unit]
}

object AlertEngine {
  def make[F[_]: Logger: Monad: Time](
      producer: Producer[F, Alert],
      snapshots: SnapshotReader[F]
  ): AlertEngine[F] =
    new AlertEngine[F] {
      val run: Pipe[F, TradeEvent, Unit] =
        events =>
          Stream.eval(snapshots.latest).flatMap { maybeSt =>
            events
              .evalMapAccumulate(maybeSt.getOrElse(TradeState.empty) -> DedupState.empty) {
                case ((st, ds), CommandExecuted(command, _)) =>
                  Conflicts.dedup(ds)(command) match {
                    case None =>
                      Logger[F].warn(s"Deduplicating Command ID: ${command.id.show}").tupleLeft(st -> ds)
                    case Some(cmd) =>
                      val nst = EventSource.runS(st)(cmd)
                      val p   = st.prices.get(cmd.symbol)
                      val c   = nst.prices.get(cmd.symbol)

                      val previousAskMax: AskPrice = p.flatMap(_.ask.keySet.maxOption).getOrElse(0.0)
                      val previousBidMax: BidPrice = p.flatMap(_.bid.keySet.maxOption).getOrElse(0.0)
                      val currentAskMax: AskPrice  = c.flatMap(_.ask.keySet.maxOption).getOrElse(0.0)
                      val currentBidMax: BidPrice  = c.flatMap(_.bid.keySet.maxOption).getOrElse(0.0)

                      val high: Price = c.map(_.high).getOrElse(0.0)
                      val low: Price  = c.map(_.low).getOrElse(0.0)

                      // dummy logic to simulate the trading market
                      val alert: Alert =
                        if (previousAskMax - currentAskMax > 0.3)
                          Alert(StrongBuy, cmd.symbol, currentAskMax, currentBidMax, high, low)
                        else if (previousAskMax - currentAskMax > 0.2)
                          Alert(Buy, cmd.symbol, currentAskMax, currentBidMax, high, low)
                        else if (currentBidMax - previousBidMax > 0.3)
                          Alert(StrongSell, cmd.symbol, currentAskMax, currentBidMax, high, low)
                        else if (currentBidMax - previousBidMax > 0.2)
                          Alert(Sell, cmd.symbol, currentAskMax, currentBidMax, high, low)
                        else
                          Alert(Neutral, cmd.symbol, currentAskMax, currentBidMax, high, low)

                      Time[F].timestamp.flatMap { ts =>
                        val nds = Conflicts.update(ds)(cmd, ts)
                        producer.send(alert).tupleLeft(nst -> nds)
                      }
                  }
              }
              .void
          }
    }
}
