package trading.ws

import trading.core.AppTopic
import trading.core.http.Ember
import trading.domain.Alert
import trading.lib.{ Consumer, Logger }

import cats.effect.*
import dev.profunktor.pulsar.{ Pulsar, Subscription }
import fs2.Stream
import fs2.concurrent.Topic

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { (consumer, topic, server) =>
        val http =
          Stream.eval(server.useForever)

        val subs =
          topic.subscribers.evalMap(n => Logger[IO].info(s"WS connections: $n"))

        val alerts =
          consumer.receiveM.evalMap { case Consumer.Msg(id, alert) =>
            topic.publish1(alert) *> consumer.ack(id)
          }

        Stream(http, subs, alerts).parJoin(3)
      }
      .compile
      .drain

  val sub =
    Subscription.Builder
      .withName("ws-server")
      .withType(Subscription.Type.Shared)
      .build

  def resources =
    for
      config <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.make[IO](config.pulsar.url)
      _      <- Resource.eval(Logger[IO].info("Initializing ws-server service"))
      ptopic = AppTopic.Alerts.make(config.pulsar)
      consumer <- Consumer.pulsar[IO, Alert](pulsar, ptopic, sub)
      topic    <- Resource.eval(Topic[IO, Alert])
      server = Ember.websocket[IO](config.httpPort, WsRoutes[IO](_, topic).routes)
    yield (consumer, topic, server)
