package trading.snapshots

import trading.core.snapshots.SnapshotWriter
import trading.core.{ AppTopic, EventSource }
import trading.events.TradeEvent
import trading.lib.Consumer
import trading.lib.inject._
import trading.state.TradeState

import cats.effect._
import cats.syntax.all._
import cr.pulsar.{ Config, Pulsar, Subscription }
import dev.profunktor.redis4cats.effect.Log.Stdout._
import fs2.Stream

object Main extends IOApp.Simple {

  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { case (consumer, snapshots) =>
        consumer.receive
          .evalMapAccumulate(TradeState.empty) { case (st, evt) =>
            IO.unit.tupleLeft(EventSource.runS(st)(evt.command))
          }
          .map(_._1)
          .chunkN(5) // persist snapshots every 5th event
          .evalMap {
            _.last.traverse_ { st =>
              IO.println(s"Saving snapshot: $st") >> snapshots.save(st)
            }
          }
      }
      .compile
      .drain

  val config = Config.Builder.default

  val topic = AppTopic.TradingEvents.make(config)

  val sub =
    Subscription.Builder
      .withName("snapshots-sub")
      .withType(Subscription.Type.Shared)
      .build

  def resources =
    for {
      pulsar    <- Pulsar.make[IO](config.url)
      _         <- Resource.eval(IO.println(">>> Initializing snapshots service <<<"))
      consumer  <- Consumer.pulsar[IO, TradeEvent](pulsar, topic, sub)
      snapshots <- SnapshotWriter.make[IO]
    } yield consumer -> snapshots

}
