package trading.core.lib

import cats.effect.kernel.{ Async, Resource }
import cats.effect.std.{ Console, Queue }
import cats.{ Parallel, Show }
import cr.pulsar.schema.Schema
import cr.pulsar.{ Producer => PulsarProducer, _ }

trait Producer[F[_], A] {
  def send(a: A): F[Unit]
}

object Producer {
  def from[F[_], A](queue: Queue[F, Option[A]]): Producer[F, A] =
    new Producer[F, A] {
      def send(a: A): F[Unit] = queue.offer(Some(a))
    }

  def stdout[F[_]: Console, A: Show]: Producer[F, A] =
    new Producer[F, A] {
      def send(a: A): F[Unit] = Console[F].println(a)
    }

  def pulsar[F[_]: Async: Parallel, A: Schema](
      client: Pulsar.T,
      topic: Topic.Single
  ): Resource[F, Producer[F, A]] =
    PulsarProducer.make[F, A](client, topic).map { p =>
      new Producer[F, A] {
        def send(a: A): F[Unit] = p.send_(a)
      }
    }
}
