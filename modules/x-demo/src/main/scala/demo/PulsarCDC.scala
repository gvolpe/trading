package demo

import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID

import scala.concurrent.duration.*

import trading.domain.*
import trading.lib.{ Consumer, Logger }
import trading.lib.Logger.NoOp.given

import cats.effect.*
import cats.effect.syntax.all.*
import cats.syntax.all.*
import dev.profunktor.pulsar.{ Config as PulsarConfig, Producer as PulsarProducer, Pulsar, Subscription, Topic }
import doobie.Transactor
import doobie.*
import doobie.implicits.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import fs2.Stream
import io.circe.Codec

/*
 * A little application that inserts three different records in the authors table,
 * and a Pulsar consumer receiving data from the Debezium Postgres connector.
 *
 * Comment out the `Logger.NoOp.given` import to see incoming Pulsar messages.
 */
object PulsarCDC extends IOApp.Simple:
  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { consumer =>
        val c = consumer.receive.evalMap {
          case CDC(None, Some(a))    => IO.println(s"INSERTED: $a")
          case CDC(Some(a), None)    => IO.println(s"DELETED: $a")
          case CDC(Some(_), Some(a)) => IO.println(s"UPDATED: $a")
          case CDC(None, None)       => IO.unit
        }
        val p = Stream.emits(authors).metered[IO](1.second).evalMap(PG.insertAuthor)
        c.concurrently(p).interruptAfter(5.seconds)
      }
      .compile
      .drain

  val authors = List(
    "Gabriel Volpe"  -> "gvolpe.com",
    "John Doe"       -> "jdoe.net",
    "Martin Odersky" -> "https://people.epfl.ch/martin.odersky/?lang=en"
  ).map { (k, v) =>
    AuthorDTO(AuthorId(UUID.randomUUID()), AuthorName(k), Some(Website(v)))
  }

  val config = PulsarConfig.Builder.default

  // FORMAT: persistent://public/default/${database.server.name}.${schema}.${table}
  val topic =
    Topic.Builder
      .withName(Topic.Name("dbserver.public.authors"))
      .withConfig(config)
      .withType(Topic.Type.Persistent)
      .build

  val sub =
    Subscription.Builder
      .withName("pg-authors")
      .withType(Subscription.Type.Exclusive)
      .build

  def resources =
    for
      pulsar   <- Pulsar.make[IO](config.url)
      _        <- PG.deleteAuthors.toResource
      consumer <- Consumer.pulsar[IO, CDC](pulsar, topic, sub)
    yield consumer

final case class AuthorDTO(
    id: AuthorId,
    name: AuthorName,
    website: Option[Website]
) derives Codec.AsObject

case class CDC(
    before: Option[AuthorDTO],
    after: Option[AuthorDTO]
) derives Codec.AsObject

object PG:
  private val xa: Transactor[IO] =
    Transactor.fromDriverManager[IO](
      "org.postgresql.Driver",   // driver classname
      "jdbc:postgresql:trading", // connect URL (driver-specific)
      "postgres",                // user
      "postgres",                // password
      None                       // log handler
    )

  val deleteAuthors: IO[Unit] = sql"""
    DELETE FROM authors
  """.update.run.transact(xa).void

  val insertAuthor: AuthorDTO => IO[Unit] = a => sql"""
    INSERT INTO authors (id, name, website)
    VALUES (${a.id.value}, ${a.name.value}, ${a.website.map(_.value)})
  """.update.run.transact(xa).void
