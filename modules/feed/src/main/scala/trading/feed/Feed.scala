package trading.feed

import scala.concurrent.duration.*

import trading.commands.TradeCommand
import trading.domain.generators.*
import trading.lib.{ GenUUID, Logger, Producer }

import cats.effect.kernel.Temporal
import cats.syntax.all.*

trait Feed[F[_]]:
  def run: F[Unit]

object Feed:
  def random[F[_]: GenUUID: Logger: Temporal](
      producer: Producer[F, TradeCommand]
  ): Feed[F] =
    new Feed[F] {
      def run: F[Unit] =
        commandsGen.replicateA(2).flatten.traverse_ { cmd =>
          GenUUID[F].random.flatMap { cmdId =>
            val uniqueCmd = TradeCommand._CommandId.replace(cmdId)(cmd)
            Logger[F].info(cmd.show) >> producer.send(uniqueCmd) >> Temporal[F].sleep(300.millis)
          }
        }
    }
