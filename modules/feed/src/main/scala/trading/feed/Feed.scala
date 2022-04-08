package trading.feed

import scala.concurrent.duration.*

import trading.commands.*
import trading.domain.*
import trading.domain.TradingStatus.*
import trading.domain.generators.*
import trading.lib.*

import cats.Parallel
import cats.effect.kernel.{ Ref, Temporal }
import cats.syntax.all.*

object Feed:
  def random[F[_]: GenUUID: Logger: Parallel: Temporal: Time](
      trProducer: Producer[F, TradeCommand],
      switcher: Producer[F, SwitchCommand],
      fcProducer: Producer[F, ForecastCommand]
  ): F[Unit] =
    val trading: F[Unit] =
      Ref.of[F, TradingStatus](TradingStatus.On).flatMap { ref =>
        def switch(st: TradingStatus, cmd: SwitchCommand): F[Unit] =
          (Time[F].timestamp, GenUUID[F].make[CommandId]).tupled.flatMap { (ts, cmdId) =>
            import SwitchCommand.*
            val uniqueCmd = _CommandId.replace(cmdId).andThen(_CreatedAt.replace(ts))(cmd)
            Logger[F].warn(s">>> Trading $st <<<") *>
              switcher.send(uniqueCmd) *> ref.set(On) *> Temporal[F].sleep(1.second)
          }

        def trade(cmd: TradeCommand): F[Unit] =
          (ref.get, Time[F].timestamp, GenUUID[F].make[CommandId]).tupled.flatMap {
            case (On, ts, cmdId) =>
              import TradeCommand.*
              val uniqueCmd = _CommandId.replace(cmdId).andThen(_CreatedAt.replace(ts))(cmd)
              Logger[F].info(cmd.show) *> trProducer.send(uniqueCmd) *> Temporal[F].sleep(300.millis)
            case (Off, _, _) =>
              ().pure[F]
          }

        tradeCommandListGen.replicateA(2).flatten.traverse_ {
          case cmd @ SwitchCommand.Start(_, _, _) =>
            switch(On, cmd)
          case cmd @ SwitchCommand.Stop(_, _, _) =>
            switch(Off, cmd)
          case cmd: TradeCommand =>
            trade(cmd)
        }
      }

    val forecasting: F[Unit] =
      forecastCommandListGen.replicateA(2).flatten.traverse_ { cmd =>
        import ForecastCommand.*

        (Time[F].timestamp, GenUUID[F].make[CommandId]).tupled.flatMap { (ts, cmdId) =>
          val uniqueCmd = _CommandId.replace(cmdId).andThen(_CreatedAt.replace(ts))(cmd)
          Logger[F].info(cmd.show) *> fcProducer.send(uniqueCmd) *> Temporal[F].sleep(300.millis)
        }
      }

    trading &> forecasting
