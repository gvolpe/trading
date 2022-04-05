package trading.lib

import java.nio.charset.StandardCharsets.UTF_8

import cats.Applicative
import cats.effect.kernel.{ Async, Ref, Resource }
import cats.effect.std.Queue
import cats.syntax.all.*
import dev.profunktor.pulsar.{ Consumer as PulsarConsumer, * }
import dev.profunktor.pulsar.transactions.Tx
import fs2.Stream
import fs2.kafka.{ ConsumerSettings, KafkaConsumer }
import io.circe.{ Decoder, Encoder }
import io.circe.parser.decode as jsonDecode
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.pulsar.client.api.{ DeadLetterPolicy, MessageId }

trait Acker[F[_], A]:
  def ack(id: Consumer.MsgId): F[Unit]
  def ack(ids: Set[Consumer.MsgId]): F[Unit]
  def ack(id: Consumer.MsgId, tx: Txn): F[Unit]
  def nack(id: Consumer.MsgId): F[Unit]

trait Consumer[F[_], A] extends Acker[F, A]:
  def receiveM: Stream[F, Consumer.Msg[A]]
  def receiveM(id: Consumer.MsgId): Stream[F, Consumer.Msg[A]]
  def receive: Stream[F, A]

object Consumer:
  //Pulsar: serialized version of MessageId(ledgerId: Long, entryId: Long, partitionIndex: Int)
  type MsgId      = String
  type Properties = Map[String, String]

  final case class Msg[A](id: MsgId, props: Properties, payload: A)

  def local[F[_]: Applicative, A](
      queue: Queue[F, Option[A]]
  ): Consumer[F, A] = new:
    def receiveM: Stream[F, Msg[A]]                              = receive.map(Msg("N/A", Map.empty, _))
    def receiveM(id: Consumer.MsgId): Stream[F, Consumer.Msg[A]] = receiveM
    def receive: Stream[F, A]                                    = Stream.fromQueueNoneTerminated(queue)
    def ack(id: Consumer.MsgId): F[Unit]                         = Applicative[F].unit
    def ack(ids: Set[Consumer.MsgId]): F[Unit]                   = Applicative[F].unit
    def ack(id: Consumer.MsgId, tx: Txn): F[Unit]                = Applicative[F].unit
    def nack(id: Consumer.MsgId): F[Unit]                        = Applicative[F].unit

  def pulsar[F[_]: Async: Logger, A: Decoder: Encoder](
      client: Pulsar.T,
      topic: Topic,
      sub: Subscription,
      settings: Option[PulsarConsumer.Settings[F, A]] = None
  ): Resource[F, Consumer[F, A]] =
    val deadLetterPolicy =
      DeadLetterPolicy.builder.deadLetterTopic("dead-letter").maxRedeliverCount(2).build

    val _settings =
      settings
        .getOrElse(PulsarConsumer.Settings[F, A]())
        .withLogger(Logger.pulsar[F, A]("in"))
        .withDeadLetterPolicy(deadLetterPolicy)

    val decoder: Array[Byte] => F[A] =
      bs => Async[F].fromEither(jsonDecode[A](new String(bs, UTF_8)))

    val handler: Throwable => F[PulsarConsumer.OnFailure] =
      e => Logger[F].error(e.getMessage).as(PulsarConsumer.OnFailure.Nack)

    PulsarConsumer.make[F, A](client, topic, sub, decoder, handler, _settings).map { c =>
      new:
        def receiveM: Stream[F, Msg[A]] = c.subscribe.map { m =>
          Msg(new String(m.id.toByteArray(), UTF_8), m.properties, m.payload)
        }
        def receiveM(id: Consumer.MsgId): Stream[F, Consumer.Msg[A]] =
          c.subscribe(MessageId.fromByteArray(id.getBytes(UTF_8))).map { m =>
            Msg(new String(m.id.toByteArray(), UTF_8), m.properties, m.payload)
          }

        def receive: Stream[F, A]                  = c.autoSubscribe
        def ack(id: Consumer.MsgId): F[Unit]       = c.ack(MessageId.fromByteArray(id.getBytes(UTF_8)))
        def ack(ids: Set[Consumer.MsgId]): F[Unit] = c.ack(ids.map(id => MessageId.fromByteArray(id.getBytes(UTF_8))))
        def ack(id: Consumer.MsgId, tx: Txn): F[Unit] = c.ack(MessageId.fromByteArray(id.getBytes(UTF_8)), tx.get)
        def nack(id: Consumer.MsgId): F[Unit]         = c.nack(MessageId.fromByteArray(id.getBytes(UTF_8)))
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
                ref.set(c.offset.offsets).as(Msg("N/A", Map.empty, c.record.value))
              }
            def receiveM(id: Consumer.MsgId): Stream[F, Consumer.Msg[A]] = receiveM
            def receive: Stream[F, A] =
              c.stream.evalMap(c => c.offset.commit.as(c.record.value))
            def ack(id: MsgId): F[Unit] =
              ref.get >>= c.commitAsync
            def ack(ids: Set[Consumer.MsgId]): F[Unit] =
              ref.get >>= c.commitAsync
            def ack(id: MsgId, tx: Txn): F[Unit] =
              ack(id)
            def nack(id: Consumer.MsgId): F[Unit] =
              Applicative[F].unit
        }
    }
