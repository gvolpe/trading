package trading.lib

import java.nio.charset.StandardCharsets.UTF_8

import cats.Applicative
import cats.effect.kernel.{ Async, Ref, Resource }
import cats.effect.std.Queue
import cats.syntax.all.*
import dev.profunktor.pulsar.{ Consumer as PulsarConsumer, * }
import fs2.Stream
import fs2.kafka.{ ConsumerSettings, KafkaConsumer }
import io.circe.{ Decoder, Encoder }
import io.circe.parser.decode as jsonDecode
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.pulsar.client.api.MessageId

trait Consumer[F[_], A]:
  def ack(id: Consumer.MsgId): F[Unit]
  def nack(id: Consumer.MsgId): F[Unit]
  def receiveM: Stream[F, Consumer.Msg[A]]
  def receive: Stream[F, A]

object Consumer:
  type MsgId = String

  final case class Msg[A](id: MsgId, payload: A)

  def local[F[_]: Applicative, A](
      queue: Queue[F, Option[A]]
  ): Consumer[F, A] = new:
    def receiveM: Stream[F, Msg[A]]       = receive.map(Msg("N/A", _))
    def receive: Stream[F, A]             = Stream.fromQueueNoneTerminated(queue)
    def ack(id: Consumer.MsgId): F[Unit]  = Applicative[F].unit
    def nack(id: Consumer.MsgId): F[Unit] = Applicative[F].unit

  def pulsar[F[_]: Async: Logger, A: Decoder: Encoder](
      client: Pulsar.T,
      topic: Topic,
      sub: Subscription,
      settings: Option[PulsarConsumer.Settings[F, A]] = None
  ): Resource[F, Consumer[F, A]] =
    val _settings =
      settings
        .getOrElse(PulsarConsumer.Settings[F, A]())
        .withLogger(Logger.pulsar[F, A]("in"))

    val decoder: Array[Byte] => F[A] =
      bs => Async[F].fromEither(jsonDecode[A](new String(bs, UTF_8)))

    val handler: Throwable => F[PulsarConsumer.OnFailure] =
      e => Logger[F].error(e.getMessage).as(PulsarConsumer.OnFailure.Ack)

    PulsarConsumer.make[F, A](client, topic, sub, decoder, handler, _settings).map { c =>
      new:
        def receiveM: Stream[F, Msg[A]] = c.subscribe.map(m => Msg(new String(m.id.toByteArray(), UTF_8), m.payload))
        def receive: Stream[F, A]       = c.autoSubscribe
        def ack(id: Consumer.MsgId): F[Unit]  = c.ack(MessageId.fromByteArray(id.getBytes(UTF_8)))
        def nack(id: Consumer.MsgId): F[Unit] = c.nack(MessageId.fromByteArray(id.getBytes(UTF_8)))
    }

  type KafkaOffset = Map[TopicPartition, OffsetAndMetadata]

  def kafka[F[_]: Async, A](
      settings: ConsumerSettings[F, String, A],
      topic: String
  ): Resource[F, Consumer[F, A]] =
    Resource.eval(Ref.of[F, KafkaOffset](Map.empty)).flatMap { ref =>
      KafkaConsumer
        .resource[F, String, A](settings.withEnableAutoCommit(false))
        .evalTap(_.subscribeTo(topic))
        .map { c =>
          new:
            def receiveM: Stream[F, Msg[A]] =
              c.stream.evalMap { c =>
                ref.set(c.offset.offsets).as(Msg("N/A", c.record.value))
              }
            def receive: Stream[F, A] =
              c.stream.evalMap(c => c.offset.commit.as(c.record.value))
            def ack(id: MsgId): F[Unit] =
              ref.get >>= c.commitAsync
            def nack(id: Consumer.MsgId): F[Unit] =
              Applicative[F].unit
        }
    }
