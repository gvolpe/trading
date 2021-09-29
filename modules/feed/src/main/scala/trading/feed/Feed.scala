package trading.feed

import scala.concurrent.duration._

import trading.commands.TradeCommand
import trading.domain.generators._
import trading.lib.{ Logger, Producer }

import cats.effect.kernel.Temporal
import cats.syntax.all._

trait Feed[F[_]] {
  def run: F[Unit]
}

object Feed {
  def random[F[_]: Logger: Temporal](
      producer: Producer[F, TradeCommand]
  ): Feed[F] =
    new Feed[F] {
      def run: F[Unit] =
        commandsGen.replicateA(2).flatten.traverse_ { cmd =>
          Logger[F].info(cmd.show) >> producer.send(cmd) >> Temporal[F].sleep(300.millis)
        }
    }
}
