package trading.feed

import trading.commands.TradeCommand
import trading.core.{ Producer, Time }
import trading.domain.TradeAction

import fs2.Stream
import cats.FlatMap
import cats.syntax.all._

trait Feed[F[_]] {
  def run: Stream[F, Unit]
}

object Feed {
  def random[F[_]: FlatMap: Time](
      producer: Producer[F, TradeCommand]
  ): Feed[F] =
    new Feed[F] {
      // TODO: Emit random commands every sec and stop after 10 secs or so
      def run: Stream[F, Unit] =
        Stream.eval {
          Time[F].timestamp.flatMap { ts =>
            val cmd = TradeCommand.Create("EURPLN", TradeAction.Ask, 4.57484, 10, "test", ts)
            producer.send(cmd)
          }
        }
    }
}
