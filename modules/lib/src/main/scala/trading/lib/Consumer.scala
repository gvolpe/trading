package trading.lib

import java.nio.charset.StandardCharsets.UTF_8

import scala.concurrent.duration.*

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

trait Consumer[F[_], A] extends Acker[F, A]:
  def receiveM: Stream[F, Consumer.Msg[A]]
  def receiveM(id: Consumer.MsgId): Stream[F, Consumer.Msg[A]]
  def receive: Stream[F, A]
  def lastMsgId: F[Option[Consumer.MsgId]]

object Consumer:
  //Pulsar: MessageId(ledgerId: Long, entryId: Long, partitionIndex: Int)
  enum MsgId:
    case Pulsar(id: MessageId)
    case Dummy
    case Test(id: String)

    def serialize: String =
      new String(getPulsar.toByteArray, UTF_8)

    def getPulsar: MessageId =
      this match
        case Pulsar(mid) => mid
        case _           => throw new IllegalArgumentException()

  type Properties = Map[String, String]

  object MsgId:
    def earliest: MsgId          = MsgId.Pulsar(MessageId.earliest)
    def from(str: String): MsgId = MsgId.Pulsar(MessageId.fromByteArray(str.getBytes(UTF_8)))

  final case class Msg[A](id: MsgId, props: Properties, payload: A)

  def local[F[_]: Applicative, A](
      queue: Queue[F, Option[A]]
  ): Consumer[F, A] = new:
    def receiveM: Stream[F, Msg[A]]            = receive.map(Msg(MsgId.Dummy, Map.empty, _))
    def receiveM(id: MsgId): Stream[F, Msg[A]] = receiveM
    def receive: Stream[F, A]                  = Stream.fromQueueNoneTerminated(queue)
    def ack(id: MsgId): F[Unit]                = Applicative[F].unit
    def ack(ids: Set[MsgId]): F[Unit]          = Applicative[F].unit
    def ack(id: MsgId, tx: Txn): F[Unit]       = Applicative[F].unit
    def nack(id: MsgId): F[Unit]               = Applicative[F].unit
    def lastMsgId: F[Option[MsgId]]            = none.pure[F]

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
          Msg(MsgId.Pulsar(m.id), m.properties, m.payload)
        }
        def receiveM(id: MsgId): Stream[F, Consumer.Msg[A]] =
          c.subscribe(id.getPulsar).map { m =>
            Msg(MsgId.Pulsar(m.id), m.properties, m.payload)
          }

        def receive: Stream[F, A]            = c.autoSubscribe
        def lastMsgId: F[Option[MsgId]]      = c.lastMessageId.map(_.map(MsgId.Pulsar(_)))
        def ack(id: MsgId): F[Unit]          = c.ack(id.getPulsar)
        def ack(ids: Set[MsgId]): F[Unit]    = c.ack(ids.map(_.getPulsar))
        def ack(id: MsgId, tx: Txn): F[Unit] = c.ack(id.getPulsar, tx.get)
        def nack(id: MsgId): F[Unit]         = c.nack(id.getPulsar)
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
                ref.set(c.offset.offsets).as(Msg(MsgId.Dummy, Map.empty, c.record.value))
              }
            def receiveM(id: MsgId): Stream[F, Consumer.Msg[A]] = receiveM
            def receive: Stream[F, A]            = c.stream.evalMap(c => c.offset.commit.as(c.record.value))
            def lastMsgId: F[Option[MsgId]]      = none.pure[F]
            def ack(id: MsgId): F[Unit]          = ref.get >>= c.commitAsync
            def ack(ids: Set[MsgId]): F[Unit]    = ref.get >>= c.commitAsync
            def ack(id: MsgId, tx: Txn): F[Unit] = ack(id)
            def nack(id: MsgId): F[Unit]         = Applicative[F].unit
        }
    }
