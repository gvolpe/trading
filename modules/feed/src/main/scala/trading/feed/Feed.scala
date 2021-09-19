package trading.feed

import scala.concurrent.duration._

import trading.commands.TradeCommand
import trading.core.lib.Producer
import trading.feed.generators._

import cats.effect.kernel.Temporal
import cats.syntax.all._

trait Feed[F[_]] {
  def run: F[Unit]
}

object Feed {
  def random[F[_]: Temporal](
      producer: Producer[F, TradeCommand]
  ): Feed[F] =
    new Feed[F] {
      def run: F[Unit] =
        commandsGen.replicateA(2).flatten.traverse_ {
          producer.send(_) >> Temporal[F].sleep(100.millis)
        }
    }
}
