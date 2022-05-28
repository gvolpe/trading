package trading.lib

import java.nio.charset.StandardCharsets.UTF_8

import cats.Eq
import cats.effect.kernel.{ Async, Ref, Resource }
import cats.effect.std.Queue
import cats.syntax.all.*
import cats.{ Applicative, Parallel, Show }
import dev.profunktor.pulsar.{ Producer as PulsarProducer, * }
import fs2.kafka.{ KafkaProducer, ProducerSettings }
import io.circe.Encoder
import io.circe.syntax.*

trait Producer[F[_], A]:
  def send(a: A): F[Unit]
  def send(a: A, properties: Map[String, String]): F[Unit]

object Producer:
  def local[F[_]: Applicative, A](queue: Queue[F, Option[A]]): Resource[F, Producer[F, A]] =
    Resource.make[F, Producer[F, A]](
      Applicative[F].pure(
        new:
          def send(a: A): F[Unit]                                  = queue.offer(Some(a))
          def send(a: A, properties: Map[String, String]): F[Unit] = send(a)
      )
    )(_ => queue.offer(None))

  def dummy[F[_]: Applicative, A]: Producer[F, A] = new:
    def send(a: A): F[Unit]                                  = Applicative[F].unit
    def send(a: A, properties: Map[String, String]): F[Unit] = send(a)

  def testMany[F[_], A](ref: Ref[F, List[A]]): Producer[F, A] = new:
    def send(a: A): F[Unit]                                  = ref.update(_ :+ a)
    def send(a: A, properties: Map[String, String]): F[Unit] = send(a)

  def test[F[_], A](ref: Ref[F, Option[A]]): Producer[F, A] = new:
    def send(a: A): F[Unit]                                  = ref.set(Some(a))
    def send(a: A, properties: Map[String, String]): F[Unit] = send(a)

  private def dummySeqIdMaker[F[_]: Applicative, A]: SeqIdMaker[F, A] = new:
    def make(lastSeqId: Long, currentMsg: A): F[Long] = Applicative[F].pure(0L)

  def pulsar[F[_]: Async: Logger: Parallel, A: Encoder](
      client: Pulsar.T,
      topic: Topic.Single,
      settings: Option[PulsarProducer.Settings[F, A]] = None
  ): Resource[F, Producer[F, A]] =
    val _settings =
      settings
        .getOrElse(PulsarProducer.Settings[F, A]())
        .withLogger(Logger.pulsar[F, A]("out"))

    val encoder: A => Array[Byte] = _.asJson.noSpaces.getBytes(UTF_8)

    PulsarProducer.make[F, A](client, topic, encoder, _settings).map { p =>
      new:
        def send(a: A): F[Unit]                                  = p.send_(a)
        def send(a: A, properties: Map[String, String]): F[Unit] = p.send_(a, properties)
    }

  def kafka[F[_]: Async, A](
      settings: ProducerSettings[F, String, A],
      topic: String
  ): Resource[F, Producer[F, A]] =
    KafkaProducer.resource(settings).map { p =>
      new:
        def send(a: A): F[Unit] =
          p.produceOne_(topic, "key", a).flatten.void
        def send(a: A, properties: Map[String, String]): F[Unit] = send(a)
    }
