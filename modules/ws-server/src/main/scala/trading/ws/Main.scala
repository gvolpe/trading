package trading.ws

import trading.core.AppTopic
import trading.core.http.Ember
import trading.domain.Alert
import trading.lib.Consumer
import trading.lib.inject.given

import cats.effect.*
import dev.profunktor.pulsar.{ Config, Pulsar, Subscription }
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

  val config = Config.Builder.default

  val sub =
    Subscription.Builder
      .withName("ws-server-sub")
      .withType(Subscription.Type.Shared)
      .build

  val topic = AppTopic.Alerts.make(config)

  def resources =
    for {
      pulsar <- Pulsar.make[IO](config.url)
      _      <- Resource.eval(IO.println(">>> Initializing ws-server service <<<"))
      alerts <- Consumer.pulsar[IO, Alert](pulsar, topic, sub).map(_.receive)
      topic  <- Resource.eval(Topic[IO, Alert])
      server = Ember.websocket[IO](WsRoutes[IO](_, topic).routes)
    } yield (alerts, topic, server)
