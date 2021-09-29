package trading.processor

import trading.commands.TradeCommand
import trading.core.snapshots.SnapshotReader
import trading.core.{ Conflicts, EventSource }
import trading.events.TradeEvent
import trading.lib.{ Logger, Producer, Time }
import trading.state.{ DedupState, TradeState }

import cats.Monad
import cats.syntax.all._
import fs2.{ Pipe, Stream }

trait Engine[F[_]] {
  def run: Pipe[F, TradeCommand, Unit]
}

object Engine {
  def make[F[_]: Logger: Monad: Time](
      producer: Producer[F, TradeEvent],
      snapshots: SnapshotReader[F]
  ): Engine[F] =
    new Engine[F] {
      def run: Pipe[F, TradeCommand, Unit] =
        commands =>
          Stream
            .eval(snapshots.latest.map(_.getOrElse(TradeState.empty)))
            .flatMap { latest =>
              Stream.eval(Logger[F].info(s">>> SNAPSHOTS: $latest")) >>
                commands
                  .evalMapAccumulate(latest -> DedupState.empty) { case ((st, ds), command) =>
                    Conflicts.dedup(ds)(command) match {
                      case None =>
                        Logger[F].warn(s"Deduplicating Command ID: ${command.id.show}").tupleLeft(st -> ds)
                      case Some(cmd) =>
                        val (nst, events) = EventSource.run(st)(cmd)
                        events
                          .traverse(Time[F].timestamp.map(_))
                          .flatTap(_.traverse_(producer.send))
                          .flatMap { xs =>
                            Time[F].timestamp
                              .map { now =>
                                val nds = Conflicts.updateMany(ds)(xs.map(_.command), now)
                                (nst -> nds) -> ()
                              }
                          }
                    }
                  }
            }
            .void
    }
}
