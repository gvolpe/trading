package demo.tracer

import java.util.UUID

import scala.concurrent.duration.*

import demo.tracer.db.UsersDB
import demo.tracer.http.*
export demo.tracer.NT.syntax.*

import trading.core.http.Ember
import trading.lib.{ given, * }
import trading.lib.Consumer.Msg
import trading.trace.Config.HoneycombApiKey
import trading.trace.tracer.Honeycomb

import cats.~>
import cats.data.Kleisli
import cats.effect.*
import cats.syntax.all.*
import com.comcast.ip4s.*
import dev.profunktor.pulsar.{ Config as PulsarConfig, Pulsar, Subscription, Topic }
import fs2.Stream
import io.circe.Codec
import org.http4s.HttpRoutes
import org.http4s.server.Server
import natchez.{ EntryPoint, Span, Trace }
import natchez.http4s.implicits.*
import natchez.Kernel

case class User(id: UUID, name: String) derives Codec.AsObject

object TraceApp extends IOApp.Simple:
  type Eff[A] = Kleisli[IO, Span[IO], A]

  def run: IO[Unit] = programTwo

  /* Users Engine.two[IO, Eff] and UsersDB.alt[IO, Eff] */
  val programOne: IO[Unit] =
    Honeycomb.makeEntryPoint(apiKey, dataset = "dist-trace-demo").use { ep =>
      ep.root("demo-root").use { root =>
        resources(ep)
          .use { (_, db, routes, nameProducer, nameConsumer, userProducer, userConsumer) =>
            val server =
              Ember.routes[IO](port"9000", routes)

            val http =
              Stream.eval(server.useForever)

            val random =
              Stream("Gabriel", "Guillermo", "Gonzalo", "Gabriel", "Miguel")
                .metered[IO](1.second)
                .evalMap { name =>
                  ep.root("random-root").use { sp =>
                    sp.put("random-name" -> name) *> sp.kernel.flatMap { k =>
                      nameProducer.sendAs(name, k.toHeaders)
                    }
                  }
                }

            val names =
              nameConsumer.receiveM.evalMap { case msg @ Msg(_, props, _) =>
                ep.continue("random-name", Kernel(props)).use { sp1 =>
                  Engine.two[IO, Eff](userProducer, db, nameConsumer.ack)(msg).run(sp1)
                }
              }

            val users =
              userConsumer.receiveM.evalMap { case Msg(id, props, user) =>
                val k = Kernel(props)
                ep.continue("ok", k).orElse(ep.continue("duplicate", k)).use { sp =>
                  sp.span("user-consumer").use { sp1 =>
                    sp1.put("user" -> user.name) *>
                      IO.println(s"<<< USER: $user with properties: $props \n") *>
                      userConsumer.ack(id)
                  }
                }
              }

            Stream(http, random, names, users).parJoin(4).compile.drain
          }
      }
    }

  /* Users Engine.one[IO] and UsersDB.make[Eff] */
  val programTwo: IO[Unit] =
    Honeycomb.makeEntryPoint(apiKey, dataset = "dist-trace-demo").use { ep =>
      ep.root("demo-root").use { root =>
        resources(ep).use { (pulsar, _, _, nameProducer, nameConsumer, _, userConsumer) =>
          contextual(ep, pulsar)
            .use { (db, routes, userProducer) =>
              Kleisli.liftF {
                val server =
                  Ember.routes[IO](port"9000", routes)

                val http =
                  Stream.eval(server.useForever)

                val random =
                  Stream("Gabriel", "Guillermo", "Gonzalo", "Gabriel", "Miguel")
                    .metered[IO](1.second)
                    .evalMap { name =>
                      ep.root("random-root").use { sp =>
                        sp.put("random-name" -> name) *> sp.kernel.flatMap { k =>
                          nameProducer.sendAs(name, k.toHeaders)
                        }
                      }
                    }

                val names =
                  nameConsumer.receiveM.evalMap { case msg @ Msg(_, props, _) =>
                    ep.continue("random-name", Kernel(props)).use { sp1 =>
                      Engine.one(userProducer, db, id => Kleisli.liftF(nameConsumer.ack(id)))(msg).run(sp1)
                    }
                  }

                val users =
                  userConsumer.receiveM.evalMap { case Msg(id, props, user) =>
                    val k = Kernel(props)
                    ep.continue("ok", k).orElse(ep.continue("duplicate", k)).use { sp =>
                      sp.span("user-consumer").use { sp1 =>
                        sp1.put("user" -> user.name) *>
                          IO.println(s"<<< USER: $user with properties: $props \n") *>
                          userConsumer.ack(id)
                      }
                    }
                  }

                Stream(http, random, names, users).parJoin(4).compile.drain
              }
            }
            .run(root)
        }
      }
    }

  val sub =
    Subscription.Builder
      .withName("tracer-demo")
      .withType(Subscription.Type.Failover)
      .build

  val pulsarCfg =
    PulsarConfig.Builder
      .withTenant("public")
      .withNameSpace("default")
      .withURL("pulsar://localhost:6650")
      .build

  val nameTopic: Topic.Single =
    Topic.Builder
      .withName(Topic.Name("user-names"))
      .withConfig(pulsarCfg)
      .withType(Topic.Type.NonPersistent)
      .build

  val userTopic: Topic.Single =
    Topic.Builder
      .withName(Topic.Name("new-users"))
      .withConfig(pulsarCfg)
      .withType(Topic.Type.NonPersistent)
      .build

  val apiKey = HoneycombApiKey(System.getenv("HONEYCOMB_API_KEY"))

  def contextual(
      ep: EntryPoint[IO],
      pulsar: Pulsar.T
  ): Resource[Eff, (UsersDB[Eff], HttpRoutes[IO], Producer[Eff, User])] =
    for
      db <- Resource.eval(UsersDB.make[Eff])
      routes = ep.liftT(Routes(db).routes)
      userProducer <- Producer.pulsar[Eff, User](pulsar, userTopic)
    yield (db, routes, userProducer)

  def resources(ep: EntryPoint[IO]) =
    for
      pulsar <- Pulsar.make[IO](pulsarCfg.url)
      _      <- Resource.eval(Logger[IO].info("Initializing processor service"))
      db     <- Resource.eval(UsersDB.alt[IO, Eff])
      routes = ep.liftT(Routes(db).routes)
      nameProducer <- Producer.pulsar[IO, String](pulsar, nameTopic)
      nameConsumer <- Consumer.pulsar[IO, String](pulsar, nameTopic, sub)
      userProducer <- Producer.pulsar[IO, User](pulsar, userTopic)
      userConsumer <- Consumer.pulsar[IO, User](pulsar, userTopic, sub)
    yield (pulsar, db, routes, nameProducer, nameConsumer, userProducer, userConsumer)
