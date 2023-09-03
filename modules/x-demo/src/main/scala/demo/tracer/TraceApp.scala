package demo.tracer

import java.util.UUID

import scala.concurrent.duration.*

import demo.tracer.db.UsersDB
import demo.tracer.http.*
export demo.tracer.NT.syntax.*

import trading.core.http.Ember
import trading.lib.*
import trading.lib.Consumer.Msg
import trading.trace.tracer.Honeycomb

import cats.data.Kleisli
import cats.effect.*
import cats.syntax.all.*
import com.comcast.ip4s.*
import dev.profunktor.pulsar.Pulsar
import fs2.Stream
import io.circe.Codec
import org.http4s.server.Server
import natchez.{ EntryPoint, Span, Trace }
import natchez.http4s.syntax.entrypoint.*

case class User(id: UUID, name: String) derives Codec.AsObject

object TraceApp extends IOApp.Simple:
  type Eff = [A] =>> Kleisli[IO, Span[IO], A]

  def run: IO[Unit] =
    Honeycomb.makeEntryPoint(apiKey, dataset = "dist-trace-demo").use { ep =>
      ep.root("demo-root").use { root =>
        val random: Producer[IO, String] => Stream[IO, Unit] = p =>
          Stream("Gabriel", "Guillermo", "Gonzalo", "Gabriel", "Miguel")
            .metered[IO](1.second)
            .evalMap { name =>
              ep.root("random-root").use { sp =>
                sp.put("random-name" -> name) *> sp.kernel.flatMap { k =>
                  p.send(name, k.headers)
                }
              }
            }

        val users: Consumer[IO, User] => Stream[IO, Unit] = c =>
          c.receiveM.evalMap { case Msg(id, props, user) =>
            val k = props.toKernel
            ep.continue("ok", k).orElse(ep.continue("duplicate", k)).use { sp =>
              sp.span("user-consumer").use { sp1 =>
                sp1.put("user" -> user.name) *>
                  IO.println(s"<<< USER: $user with properties: $props \n") *> c.ack(id)
              }
            }
          }

        def names(c: Consumer[IO, String], f: (Msg[String], Span[IO]) => IO[Unit]): Stream[IO, Unit] =
          c.receiveM.evalMap { case msg @ Msg(_, props, _) =>
            ep.continue("random-name", props.toKernel).use(f(msg, _))
          }

        programZero(ep, random, users, names)
      }
    }

  /* Engine.two[IO, Eff] and UsersDB.alt[IO, Eff] */
  def programTwo(
      ep: EntryPoint[IO],
      random: Producer[IO, String] => Stream[IO, Unit],
      users: Consumer[IO, User] => Stream[IO, Unit],
      names: (Consumer[IO, String], (Msg[String], Span[IO]) => IO[Unit]) => Stream[IO, Unit]
  ): IO[Unit] =
    resources(ep).use { (_, db, server, nameProducer, nameConsumer, userProducer, userConsumer) =>
      val runner =
        names(
          nameConsumer,
          (msg, sp) => Engine.two[IO, Eff](userProducer, db, nameConsumer.ack).apply(msg).run(sp)
        )

      Stream(
        Stream.eval(server.useForever),
        random(nameProducer),
        runner,
        users(userConsumer)
      ).parJoin(4).compile.drain
    }

  /* Engine.one[IO] and UsersDB.make[Eff] */
  def programOne(
      ep: EntryPoint[IO],
      root: Span[IO],
      random: Producer[IO, String] => Stream[IO, Unit],
      users: Consumer[IO, User] => Stream[IO, Unit],
      names: (Consumer[IO, String], (Msg[String], Span[IO]) => IO[Unit]) => Stream[IO, Unit]
  ): IO[Unit] =
    resources(ep).use { (pulsar, _, _, nameProducer, nameConsumer, _, userConsumer) =>
      contextual(ep, pulsar)
        .use { (db, server, userProducer) =>
          Kleisli.liftF {
            val runner =
              names(
                nameConsumer,
                (msg, sp) =>
                  Engine.one[Eff](userProducer, db, id => Kleisli.liftF(nameConsumer.ack(id))).apply(msg).run(sp)
              )

            Stream(
              Stream.eval(server.useForever),
              random(nameProducer),
              runner,
              users(userConsumer)
            ).parJoin(4).compile.drain
          }
        }
        .run(root)
    }

  /* Everything in IO: Engine.one[IO] and UsersDB.noTrace[IO] */
  def programZero(
      ep: EntryPoint[IO],
      random: Producer[IO, String] => Stream[IO, Unit],
      users: Consumer[IO, User] => Stream[IO, Unit],
      names: (Consumer[IO, String], (Msg[String], Span[IO]) => IO[Unit]) => Stream[IO, Unit]
  ): IO[Unit] =
    resources(ep).use { (_, _, _, nameProducer, nameConsumer, userProducer, userConsumer) =>
      ctxIOLocal(ep)
        .use { (db, server) =>
          val runner =
            names(
              nameConsumer,
              (msg, sp) =>
                Trace.ioTrace(sp).flatMap { implicit trace =>
                  Engine.one[IO](userProducer, db, nameConsumer.ack).apply(msg)
                }
            )

          Stream(
            Stream.eval(server.useForever),
            random(nameProducer),
            runner,
            users(userConsumer)
          ).parJoin(4).compile.drain
        }
    }

  def contextual(
      ep: EntryPoint[IO],
      pulsar: Pulsar.T
  ): Resource[Eff, (UsersDB[Eff], Resource[IO, Server], Producer[Eff, User])] =
    for
      db <- Resource.eval(UsersDB.make[Eff])
      routes = ep.liftT(Routes(db).routes)
      server = Ember.routes[IO](port"9000", routes)
      userProducer <- Producer.pulsar[Eff, User](pulsar, userTopic)
    yield (db, server, userProducer)

  /* it uses `Trace.ioTrace` under the hood, so only for IO */
  def ctxIOLocal(ep: EntryPoint[IO]) =
    for
      db <- Resource.eval(UsersDB.noTrace[IO])
      routes = ep.liftRoutes(Routes[IO](db).routes)
      server = Ember.routes[IO](port"9000", routes)
    yield db -> server

  def resources(ep: EntryPoint[IO]) =
    for
      pulsar <- Pulsar.make[IO](pulsarCfg.url)
      _      <- Resource.eval(Logger[IO].info("Initializing processor service"))
      db     <- Resource.eval(UsersDB.alt[IO, Eff])
      routes = ep.liftT(Routes(db).routes)
      server = Ember.routes[IO](port"9000", routes)
      nameProducer <- Producer.pulsar[IO, String](pulsar, nameTopic)
      nameConsumer <- Consumer.pulsar[IO, String](pulsar, nameTopic, sub)
      userProducer <- Producer.pulsar[IO, User](pulsar, userTopic)
      userConsumer <- Consumer.pulsar[IO, User](pulsar, userTopic, sub)
    yield (pulsar, db, server, nameProducer, nameConsumer, userProducer, userConsumer)
