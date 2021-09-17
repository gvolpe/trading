package trading.core

import fs2.Stream
import cats.effect.std.Queue
import cats.Functor

trait Consumer[F[_], A] {
  def receive: Stream[F, A]
}

object Consumer {
  def from[F[_]: Functor, A](queue: Queue[F, Option[A]]): Consumer[F, A] =
    new Consumer[F, A] {
      def receive: Stream[F, A] = Stream.fromQueueNoneTerminated(queue)
    }
}
