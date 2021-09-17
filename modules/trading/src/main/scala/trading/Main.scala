package trading

import cats.effect._
import cats.effect.std.Queue
import trading.events.TradeEvent
import trading.core.Consumer
import trading.core.Producer
import fs2.Stream
import trading.core.Time
import trading.commands.TradeCommand
import trading.domain.TradeAction
import trading.state.TradeState

object Main extends IOApp.Simple {

  val commands: Stream[IO, TradeCommand] =
    Stream.eval(Time[IO].timestamp).map { ts =>
      TradeCommand.Create("EURPLN", TradeAction.Ask, 1, 4.57484, 10, "test", ts)
    }

  val run: IO[Unit] =
    Queue.bounded[IO, Option[TradeEvent]](500).flatMap { queue =>
      val eventsConsumer = Consumer.from(queue)
      val eventsProducer = Producer.from(queue)
      val engine         = Engine.make(eventsProducer)

      engine.run(TradeState.empty)(commands).flatMap(IO.println)
    }

}
