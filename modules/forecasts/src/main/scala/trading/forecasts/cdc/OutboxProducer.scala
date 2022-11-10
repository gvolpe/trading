package trading.forecasts.cdc

import trading.lib.Producer

import cats.effect.IO
import fs2.Stream

trait OutboxProducer[F[_]]:
  def stream: Stream[F, Unit]

object OutboxProducer:
  def make(p: Producer[IO, OutboxEvent]): OutboxProducer[IO] = new:
    def stream: Stream[IO, Unit] =
      Stream.fromQueueUnterminated(OutboxState.queue).evalMap(p.send)
