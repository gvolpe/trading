package trading.lib

import java.nio.charset.StandardCharsets.UTF_8

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

object Producer:
  def local[F[_]: Applicative, A](queue: Queue[F, Option[A]]): Resource[F, Producer[F, A]] =
    Resource.make[F, Producer[F, A]](
      Applicative[F].pure(
        new:
          def send(a: A): F[Unit] = queue.offer(Some(a))
      )
    )(_ => queue.offer(None))

  def test[F[_], A](ref: Ref[F, Option[A]]): Producer[F, A] = new:
    def send(a: A): F[Unit] = ref.set(Some(a))

  def sharded[F[_]: Async: Logger: Parallel, A: Encoder: Shard](
      client: Pulsar.T,
      topic: Topic.Single
  ): Resource[F, Producer[F, A]] =
    val settings =
      PulsarProducer
        .Settings[F, A]()
        .withShardKey(Shard[A].key)
        .withLogger(Logger.pulsar[F, A]("out"))

    val encoder: A => Array[Byte] = _.asJson.noSpaces.getBytes(UTF_8)

    PulsarProducer.make[F, A](client, topic, encoder, settings).map { p =>
      new:
        def send(a: A): F[Unit] = p.send_(a)
    }

  def pulsar[F[_]: Async: Logger: Parallel, A: Encoder](
      client: Pulsar.T,
      topic: Topic.Single
  ): Resource[F, Producer[F, A]] =
    given Shard[A] = Shard.default[A]
    sharded[F, A](client, topic)

  def kafka[F[_]: Async, A](
      settings: ProducerSettings[F, String, A],
      topic: String
  ): Resource[F, Producer[F, A]] =
    KafkaProducer.resource(settings).map { p =>
      new:
        def send(a: A): F[Unit] = p.produceOne_(topic, "key", a).flatten.void
    }
