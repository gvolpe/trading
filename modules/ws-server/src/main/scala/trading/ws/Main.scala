package trading.ws

import trading.core.AppTopic
import trading.core.http.Ember
import trading.domain.{ Alert, SocketId }
import trading.lib.{ Consumer, Logger }

import cats.effect.*
import cats.syntax.all.*
import dev.profunktor.pulsar.{ Consumer as PulsarConsumer, Pulsar, Subscription }
import fs2.Stream

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { (conns, server) =>
        Stream.eval(server.useForever).concurrently {
          conns.subscriptions.evalMap { n =>
            Logger[IO].info(s"WS connections: $n")
          }
        }
      }
      .compile
      .drain

  val mkSub = (sid: SocketId) =>
    Subscription.Builder
      .withName(s"ws-server-${sid.show}")
      .withMode(Subscription.Mode.NonDurable)
      .withType(Subscription.Type.Exclusive)
      .build

  val compact =
    PulsarConsumer.Settings[IO, Alert]().withReadCompacted.some

  def resources =
    for
      config <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.make[IO](config.pulsar.url)
      _      <- Resource.eval(Logger[IO].info("Initializing ws-server service"))
      ptopic = AppTopic.Alerts.make(config.pulsar)
      conns <- Resource.eval(WsConnections.make[IO])
      mkConsumer = (sid: SocketId) => Consumer.pulsar[IO, Alert](pulsar, ptopic, mkSub(sid), compact)
      mkAlerts   = (sid: SocketId) => Stream.resource(mkConsumer(sid)).flatMap(_.receive)
      mkHandler  = (sid: SocketId) => Handler.make[IO](sid, conns, mkAlerts(sid))
      server     = Ember.websocket[IO](config.httpPort, WsRoutes[IO](_, mkHandler).routes)
    yield conns -> server
