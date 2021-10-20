package trading.snapshots

import trading.core.http.Ember
import trading.core.snapshots.{ SnapshotReader, SnapshotWriter }
import trading.core.{ AppTopic, EventSource }
import trading.events.TradeEvent
import trading.lib.Consumer
import trading.lib.inject.given
import trading.state.TradeState

import cats.effect.*
import dev.profunktor.pulsar.{ Pulsar, Subscription }
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.Log.Stdout.*
import fs2.Stream

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { (server, consumer, reader, writer) =>
        Stream.eval(server.useForever).concurrently {
          Stream
            .eval(reader.latest.map(_.getOrElse(TradeState.empty)))
            .evalTap(latest => IO.println(s">>> SNAPSHOTS: $latest"))
            .flatMap { latest =>
              consumer.receive
                .mapAccumulate(latest) { (st, evt) =>
                  EventSource.runS(st)(evt.command) -> ()
                }
                .evalMap { (st, _) =>
                  IO.println(s"Saving snapshot: $st") >> writer.save(st)
                }
            }
        }
      }
      .compile
      .drain

  // Failover subscription (it's enough to deploy two instances)
  val sub =
    Subscription.Builder
      .withName("snapshots-sub")
      .withType(Subscription.Type.Failover)
      .build

  def resources =
    for
      config <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.make[IO](config.pulsar.url)
      _      <- Resource.eval(IO.println(">>> Initializing snapshots service <<<"))
      redis  <- Redis[IO].utf8(config.redisUri.value)
      topic  = AppTopic.TradingEvents.make(config.pulsar)
      reader = SnapshotReader.fromClient(redis)
      writer = SnapshotWriter.fromClient(redis)
      consumer <- Consumer.pulsar[IO, TradeEvent](pulsar, topic, sub)
      server = Ember.default[IO]
    yield (server, consumer, reader, writer)
