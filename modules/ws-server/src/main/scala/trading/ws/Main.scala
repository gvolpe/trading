package trading.ws

import trading.core.AppTopic
import trading.domain.Alert
import trading.lib.Consumer
import trading.lib.inject._

import cats.effect._
import com.comcast.ip4s._
import cr.pulsar.{ Config, Pulsar, Subscription }
import fs2.Stream
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._

object Main extends IOApp.Simple {

  def run: IO[Unit] =
    Stream
      .resource(resources)
      .evalMap(_.useForever)
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
      api = Routes[IO](alerts).routes.orNotFound
      server = EmberServerBuilder
        .default[IO]
        .withHost(host"0.0.0.0")
        .withPort(port"9000")
        .withHttpApp(api)
        .build
        .evalMap(Ember.showBanner[IO])
    } yield server

}
