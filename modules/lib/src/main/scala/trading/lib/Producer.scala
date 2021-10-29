package trading.lib

import cats.effect.kernel.{ Async, Resource }
import cats.effect.std.{ Console, Queue }
import cats.syntax.all.*
import cats.{ Parallel, Show }
import dev.profunktor.pulsar.schema.Schema
import dev.profunktor.pulsar.{ Producer as PulsarProducer, * }
import fs2.kafka.{ KafkaProducer, ProducerSettings }

trait Producer[F[_], A]:
  def send(a: A): F[Unit]

object Producer:
  def local[F[_], A](queue: Queue[F, Option[A]]): Producer[F, A] =
    new Producer[F, A]:
      def send(a: A): F[Unit] = queue.offer(Some(a))

  def stdout[F[_]: Console, A: Show]: Producer[F, A] =
    new Producer[F, A]:
      def send(a: A): F[Unit] = Console[F].println(a)

  def sharded[F[_]: Async: Logger: Parallel, A: Schema: Shard](
      client: Pulsar.T,
      topic: Topic.Single
  ): Resource[F, Producer[F, A]] =
    PulsarProducer
      .make[F, A](
        client,
        topic,
        PulsarProducer
          .Options[F, A]()
          .withShardKey(Shard[A].key)
        //.withLogger(m => t => Logger[F].info(s"SENT: $m - Topic: $t"))
      )
      .map { p =>
        new Producer[F, A]:
          def send(a: A): F[Unit] = p.send_(a)
      }

  def pulsar[F[_]: Async: Logger: Parallel, A: Schema](
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
      new Producer[F, A]:
        def send(a: A): F[Unit] = p.produceOne_(topic, "key", a).flatten.void
    }
