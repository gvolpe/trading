package trading.lib

import java.nio.charset.StandardCharsets.UTF_8

import cats.effect.kernel.{ Async, Ref, Resource }
import cats.effect.std.{ Console, Queue }
import cats.syntax.all.*
import cats.{ Parallel, Show }
import dev.profunktor.pulsar.{ Producer as PulsarProducer, * }
import fs2.kafka.{ KafkaProducer, ProducerSettings }
import io.circe.Encoder
import io.circe.syntax.*

trait Producer[F[_], A]:
  def send(a: A): F[Unit]

object Producer:
  def local[F[_], A](queue: Queue[F, Option[A]]): Producer[F, A] = new:
    def send(a: A): F[Unit] = queue.offer(Some(a))

  def test[F[_], A](ref: Ref[F, Option[A]]): Producer[F, A] = new:
    def send(a: A): F[Unit] = ref.set(Some(a))

  def stdout[F[_]: Console, A: Show]: Producer[F, A] = new:
    def send(a: A): F[Unit] = Console[F].println(a)

  def sharded[F[_]: Async: Logger: Parallel, A: Encoder: Shard](
      client: Pulsar.T,
      topic: Topic.Single
  ): Resource[F, Producer[F, A]] =
    val settings =
      PulsarProducer
        .Settings[F, A]()
        .withShardKey(Shard[A].key)
    //.withLogger(m => t => Logger[F].info(s"SENT: $m - Topic: $t"))

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
