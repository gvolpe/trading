package trading.ws

import trading.core.AppTopic
import trading.core.http.Ember
import trading.domain.Alert
import trading.lib.Consumer

import cats.effect.*

import dev.profunktor.pulsar.{ Pulsar, Subscription }
import dev.profunktor.pulsar.schema.circe.bytes.*
import fs2.Stream
import fs2.concurrent.Topic

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { (alerts, topic, server) =>
        Stream(
          Stream.eval(server.useForever),
          topic.subscribers.evalMap(n => IO.println(s">>> WS connections: $n")),
          alerts.through(topic.publish)
        ).parJoin(3)
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
      _      <- Resource.eval(IO.println(">>> Initializing ws-server service <<<"))
      ptopic = AppTopic.Alerts.make(config.pulsar)
      alerts <- Consumer.pulsar[IO, Alert](pulsar, ptopic, sub).map(_.receive)
      topic  <- Resource.eval(Topic[IO, Alert])
      server = Ember.websocket[IO](WsRoutes[IO](_, topic).routes)
    yield (alerts, topic, server)
