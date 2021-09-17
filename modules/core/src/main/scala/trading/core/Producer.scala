package trading.core

import cats.effect.std.Queue

trait Producer[F[_], A] {
  def send(a: A): F[Unit]
}

object Producer {
  def from[F[_], A](queue: Queue[F, Option[A]]): Producer[F, A] =
    new Producer[F, A] {
      def send(a: A): F[Unit] = queue.offer(Some(a))
    }
}
