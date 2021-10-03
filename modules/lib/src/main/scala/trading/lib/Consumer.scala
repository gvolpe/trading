package trading.lib

import java.nio.charset.StandardCharsets.UTF_8

import cats.Applicative
import cats.effect.kernel.{ Async, Resource }
import cats.effect.std.Queue
import dev.profunktor.pulsar.schema.Schema
import dev.profunktor.pulsar.{ Consumer as PulsarConsumer, * }
import fs2.Stream
import fs2.kafka.{ ConsumerSettings, KafkaConsumer }
import org.apache.pulsar.client.api.MessageId

trait Consumer[F[_], A]:
  def ack(id: Consumer.MsgId): F[Unit]
  def nack(id: Consumer.MsgId): F[Unit]
  def receiveM: Stream[F, Consumer.Msg[A]]
  def receive: Stream[F, A]

object Consumer:
  type MsgId = String

  final case class Msg[A](id: MsgId, value: A)

  def local[F[_]: Applicative, A](
      queue: Queue[F, Option[A]]
  ): Consumer[F, A] =
    new Consumer[F, A] {
      def receiveM: Stream[F, Msg[A]]       = ???
      def receive: Stream[F, A]             = Stream.fromQueueNoneTerminated(queue)
      def ack(id: Consumer.MsgId): F[Unit]  = Applicative[F].unit
      def nack(id: Consumer.MsgId): F[Unit] = Applicative[F].unit
    }

  def pulsar[F[_]: Async, A: Schema](
      client: Pulsar.T,
      topic: Topic,
      sub: Subscription
  ): Resource[F, Consumer[F, A]] =
    PulsarConsumer.make[F, A](client, topic, sub).map { c =>
      new Consumer[F, A] {
        def receiveM: Stream[F, Msg[A]]       = c.subscribe.map(m => Msg(new String(m.id.toByteArray(), UTF_8), m.payload))
        def receive: Stream[F, A]             = c.autoSubscribe
        def ack(id: Consumer.MsgId): F[Unit]  = c.ack(MessageId.fromByteArray(id.getBytes(UTF_8)))
        def nack(id: Consumer.MsgId): F[Unit] = c.nack(MessageId.fromByteArray(id.getBytes(UTF_8)))
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
          // for receiveM we need to disable auto-commit, so this might not be the best abstraction
          def receiveM: Stream[F, Msg[A]]       = ???
          def receive: Stream[F, A]             = c.stream.map(_.record.value)
          def ack(id: MsgId): F[Unit]           = Applicative[F].unit
          def nack(id: Consumer.MsgId): F[Unit] = Applicative[F].unit
        }
      }
