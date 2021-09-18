package trading

import trading.commands.TradeCommand
import trading.core.inject._
import trading.core.{ AppTopic, Producer, Time }
import trading.domain.TradeAction
import trading.events.TradeEvent
import trading.state.TradeState

import cats.effect._
import cr.pulsar.{ Config, Pulsar }
import fs2.Stream
import trading.core.StateCache

object Main extends IOApp.Simple {

  val commands: Stream[IO, TradeCommand] =
    Stream.eval(Time[IO].timestamp).map { ts =>
      TradeCommand.Create("EURPLN", TradeAction.Ask, 4.57484, 10, "test", ts)
    }

  def run: IO[Unit] =
    Stream
      .resource(resources)
      // TODO: Read snapshots?
      .evalMap(_.run(TradeState.empty)(commands))
      .compile
      .drain

  val config = Config.Builder.default

  val topic = AppTopic.TradingEvents.make(config)

  def resources =
    for {
      pulsar   <- Pulsar.make[IO](config.url)
      _        <- Resource.eval(IO.println(">>> Initializing trading service <<<"))
      producer <- Producer.pulsar[IO, TradeEvent](pulsar, topic)
      cache    <- Resource.eval(StateCache.make[IO])
      //consumer <- Consumer.pulsar[IO, TradeCommand](pulsar, cmdTopic, sub)
    } yield Engine.make(producer, cache) // -> consumer

}
