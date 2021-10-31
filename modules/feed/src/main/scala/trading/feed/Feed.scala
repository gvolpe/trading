package trading.feed

import scala.concurrent.duration.*

import trading.commands.TradeCommand
import trading.commands.TradeCommand.*
import trading.domain.*
import trading.domain.TradingStatus.*
import trading.domain.generators.*
import trading.lib.*

import cats.effect.kernel.{ Ref, Temporal }
import cats.syntax.all.*

trait Feed[F[_]]:
  def run: F[Unit]

object Feed:
  def random[F[_]: GenUUID: Logger: Ref.Make: Temporal: Time](
      producer: Producer[F, TradeCommand]
  ): Feed[F] =
    new Feed[F]:
      def run: F[Unit] =
        Ref.of[F, TradingStatus](TradingStatus.On).flatMap { ref =>
          def switch(st: TradingStatus, cmd: TradeCommand): F[Unit] =
            (Time[F].timestamp, GenUUID[F].make[CommandId]).tupled.flatMap { (ts, cmdId) =>
              val uniqueCmd = _CommandId.replace(cmdId).andThen(_CreatedAt.replace(ts))(cmd)
              Logger[F].warn(s">>> Trading $st <<<") *>
                producer.send(uniqueCmd) *> ref.set(On) *> Temporal[F].sleep(1.second)
            }

          commandsGen.replicateA(2).flatten.traverse_ {
            case cmd @ TradeCommand.Start(_, _, _) =>
              switch(On, cmd)
            case cmd @ TradeCommand.Stop(_, _, _) =>
              switch(Off, cmd)
            case cmd =>
              (ref.get, Time[F].timestamp, GenUUID[F].make[CommandId]).tupled.flatMap {
                case (On, ts, cmdId) =>
                  val uniqueCmd = _CommandId.replace(cmdId).andThen(_CreatedAt.replace(ts))(cmd)
                  Logger[F].info(cmd.show) *> producer.send(uniqueCmd) *> Temporal[F].sleep(300.millis)
                case (Off, _, _) =>
                  ().pure[F]
              }
          }
        }
