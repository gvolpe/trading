package trading

import trading.commands.TradeCommand
import trading.core.inject._
import trading.core.{ AppTopic, Producer, Time }
import trading.domain.TradeAction
import trading.events.TradeEvent
import trading.state.TradeState

import cats.effect._
import cr.pulsar.{Config, Pulsar}
import fs2.Stream

object Main extends IOApp.Simple {

  val commands: Stream[IO, TradeCommand] =
    Stream.eval(Time[IO].timestamp).map { ts =>
      TradeCommand.Create("EURPLN", TradeAction.Ask, 1, 4.57484, 10, "test", ts)
    }

  def run: IO[Unit] =
    Stream
      .resource(resources)
      .evalMap { engine =>
        engine.run(TradeState.empty)(commands).flatMap(IO.println)
      }
      .compile
      .drain

  val config = Config.Builder.default

  val topic = AppTopic.TradingEvents.make(config)

  def resources =
    for {
      pulsar   <- Pulsar.make[IO](config.url)
      producer <- Producer.pulsar[IO, TradeEvent](pulsar, topic)
      //consumer <- Consumer.pulsar[IO, TradeCommand](pulsar, cmdTopic, sub)
    } yield Engine.make(producer) // -> consumer

}
