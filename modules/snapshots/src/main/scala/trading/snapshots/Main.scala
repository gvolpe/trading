package trading.snapshots

import trading.core.http.Ember
import trading.core.snapshots.{ SnapshotReader, SnapshotWriter }
import trading.core.{ AppTopic, TradeEngine }
import trading.events.TradeEvent
import trading.lib.Consumer
import trading.state.TradeState

import cats.effect.*
import dev.profunktor.pulsar.{ Consumer as PulsarConsumer, Pulsar, Subscription }
import dev.profunktor.pulsar.schema.circe.bytes.*
import dev.profunktor.redis4cats.connection.RedisClient
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
              consumer.receiveM
                .mapAccumulate(latest) { case (st, Consumer.Msg(msgId, evt)) =>
                  TradeEvent._Command.get(evt) match // TODO: is this the correct behavior?
                    case Some(cmd) =>
                      TradeEngine.fsm.runS(st, cmd) -> (msgId -> evt.id)
                    case None =>
                      st -> (msgId -> evt.id)
                }
                .evalMap { case (st, (msdId, evId)) =>
                  IO.println(s">>> Event ID: ${evId}") *>
                    writer.save(st) *> consumer.ack(msdId)
                }
            }
        }
      }
      .compile
      .drain

  // Failover subscription (it's enough to deploy two instances)
  val sub =
    Subscription.Builder
      .withName("snapshots")
      .withType(Subscription.Type.Failover)
      .build

  def resources =
    for
      config <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.make[IO](config.pulsar.url)
      _      <- Resource.eval(IO.println(">>> Initializing snapshots service <<<"))
      topic = AppTopic.TradingEvents.make(config.pulsar)
      redis    <- RedisClient[IO].from(config.redisUri.value)
      reader   <- SnapshotReader.fromClient[IO](redis)
      writer   <- SnapshotWriter.fromClient[IO](redis, config.keyExpiration)
      consumer <- Consumer.pulsar[IO, TradeEvent](pulsar, topic, sub)
      server = Ember.default[IO](config.httpPort)
    yield (server, consumer, reader, writer)
