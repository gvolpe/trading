package trading.feed

import scala.concurrent.duration.*

import trading.commands.TradeCommand
import trading.domain.*
import trading.domain.TradingStatus.*
import trading.domain.generators.*
import trading.lib.{ GenUUID, Logger, Producer }

import cats.effect.kernel.{ Ref, Temporal }
import cats.syntax.all.*

trait Feed[F[_]]:
  def run: F[Unit]

object Feed:
  def random[F[_]: GenUUID: Logger: Ref.Make: Temporal](
      producer: Producer[F, TradeCommand]
  ): Feed[F] =
    new Feed[F]:
      def run: F[Unit] =
        Ref.of[F, TradingStatus](TradingStatus.On).flatMap { ref =>
          commandsGen.replicateA(2).flatten.traverse_ {
            case TradeCommand.Start(_, _) =>
              ref.set(On)
            case TradeCommand.Stop(_, _) =>
              ref.set(Off)
            case cmd =>
              (ref.get, GenUUID[F].make[CommandId]).tupled.flatMap {
                case (On, cmdId) =>
                  val uniqueCmd = TradeCommand._CommandId.replace(cmdId)(cmd)
                  Logger[F].info(cmd.show) *> producer.send(uniqueCmd) *> Temporal[F].sleep(300.millis)
                case (Off, _) =>
                  ().pure[F]
              }
          }
        }
