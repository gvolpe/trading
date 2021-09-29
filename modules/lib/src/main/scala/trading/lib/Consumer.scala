package trading.lib

import cats.Applicative
import cats.effect.kernel.{ Async, Resource }
import cats.effect.std.Queue
import cr.pulsar.schema.Schema
import cr.pulsar.{ Consumer => PulsarConsumer, _ }
import fs2.Stream
import fs2.kafka.{ ConsumerSettings, KafkaConsumer }

trait Consumer[F[_], A] {
  def ack(id: Consumer.MsgId): F[Unit]
  def receive: Stream[F, A]
}

object Consumer {
  type MsgId = String

  def local[F[_]: Applicative, A](queue: Queue[F, Option[A]]): Consumer[F, A] =
    new Consumer[F, A] {
      def receive: Stream[F, A]            = Stream.fromQueueNoneTerminated(queue)
      def ack(id: Consumer.MsgId): F[Unit] = Applicative[F].unit
    }

  def pulsar[F[_]: Async, A: Schema](
      client: Pulsar.T,
      topic: Topic,
      sub: Subscription
  ): Resource[F, Consumer[F, A]] =
    PulsarConsumer.make[F, A](client, topic, sub).map { c =>
      new Consumer[F, A] {
        def receive: Stream[F, A]            = c.autoSubscribe
        def ack(id: Consumer.MsgId): F[Unit] = Applicative[F].unit
      }
    }

  def kafka[F[_]: Async, A](
      settings: ConsumerSettings[F, String, A],
      topic: String
  ): Resource[F, Consumer[F, A]] =
    KafkaConsumer
      .resource[F, String, A](settings.withEnableAutoCommit(true))
      .evalTap(_.subscribeTo(topic))
      .map { c =>
        new Consumer[F, A] {
          def receive: Stream[F, A]            = c.stream.map(_.record.value)
          def ack(id: Consumer.MsgId): F[Unit] = Applicative[F].unit
        }
      }
}
