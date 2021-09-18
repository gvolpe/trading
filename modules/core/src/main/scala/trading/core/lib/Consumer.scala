package trading.core.lib

import cats.Functor
import cats.effect.kernel.{ Async, Resource }
import cats.effect.std.Queue
import cr.pulsar.schema.Schema
import cr.pulsar.{ Consumer => PulsarConsumer, _ }
import fs2.Stream

trait Consumer[F[_], A] {
  def receive: Stream[F, A]
}

object Consumer {
  /* for local testing of consumer and producer in a single app */
  def from[F[_]: Functor, A](queue: Queue[F, Option[A]]): Consumer[F, A] =
    new Consumer[F, A] {
      def receive: Stream[F, A] = Stream.fromQueueNoneTerminated(queue)
    }

  def pulsar[F[_]: Async, A: Schema](
      client: Pulsar.T,
      topic: Topic,
      sub: Subscription
  ): Resource[F, Consumer[F, A]] =
    PulsarConsumer.make[F, A](client, topic, sub).map { c =>
      new Consumer[F, A] {
        def receive: Stream[F, A] = c.autoSubscribe
      }
    }
}
