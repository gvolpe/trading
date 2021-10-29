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
          def switch(st: TradingStatus, cmd: TradeCommand): F[Unit] =
            GenUUID[F].make[CommandId].flatMap { cmdId =>
              val uniqueCmd = TradeCommand._CommandId.replace(cmdId)(cmd)
              Logger[F].warn(s">>> Trading $st <<<") *>
                producer.send(uniqueCmd) *> ref.set(On) *> Temporal[F].sleep(1.second)
            }

          commandsGen.replicateA(2).flatten.traverse_ {
            case cmd @ TradeCommand.Start(_, _) =>
              switch(On, cmd)
            case cmd @ TradeCommand.Stop(_, _) =>
              switch(Off, cmd)
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
