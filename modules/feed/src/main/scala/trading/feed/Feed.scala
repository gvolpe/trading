package trading.feed

import scala.concurrent.duration._

import trading.commands.TradeCommand
import trading.domain.generators._
import trading.lib.Producer

import cats.effect.kernel.Temporal
import cats.effect.std.Console
import cats.syntax.all._

trait Feed[F[_]] {
  def run: F[Unit]
}

object Feed {
  def random[F[_]: Console: Temporal](
      producer: Producer[F, TradeCommand]
  ): Feed[F] =
    new Feed[F] {
      def run: F[Unit] =
        commandsGen.replicateA(2).flatten.traverse_ { cmd =>
          Console[F].println(cmd) >> producer.send(cmd) >> Temporal[F].sleep(100.millis)
        }
    }
}
