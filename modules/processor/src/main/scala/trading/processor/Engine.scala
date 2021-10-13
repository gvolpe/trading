package trading.processor

import trading.commands.TradeCommand
import trading.core.snapshots.SnapshotReader
import trading.core.{ Conflicts, EventSource }
import trading.events.TradeEvent
import trading.lib.*
import trading.state.{ DedupState, TradeState }

import cats.MonadThrow
import cats.syntax.all.*
import fs2.Stream

trait Engine[F[_]]:
  def run: Stream[F, Unit]

object Engine:
  def make[F[_]: Logger: MonadThrow: Time](
      consumer: Consumer[F, TradeCommand],
      producer: Producer[F, TradeEvent],
      snapshots: SnapshotReader[F]
  ): Engine[F] =
    new Engine[F]:
      def run: Stream[F, Unit] =
        Stream
          .eval(snapshots.latest.map(_.getOrElse(TradeState.empty)))
          .evalTap(latest => Logger[F].info(s">>> SNAPSHOTS: $latest"))
          .flatMap { latest =>
            consumer.receiveM
              .evalMapAccumulate(latest -> DedupState.empty) { case ((st, ds), Consumer.Msg(msgId, command)) =>
                Conflicts.dedup(ds)(command) match
                  case None =>
                    Logger[F].warn(s"Deduplicated Command ID: ${command.id.show}").tupleLeft(st -> ds)
                  case Some(cmd) =>
                    val (nst, events) = EventSource.run(st)(cmd)
                    for {
                      evs <- events.traverse(Time[F].timestamp.map(_))
                      _   <- evs.traverse(producer.send)
                      nds <- Time[F].timestamp.map(Conflicts.updateMany(ds)(evs.map(_.command), _))
                      _ <- consumer.ack(msgId).attempt.void // don't care if this fails (de-dup)
                    } yield (nst -> nds) -> ()
              }
          }
          .void
