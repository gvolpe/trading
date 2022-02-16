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
      fcProducer: Producer[F, ForecastCommand]
  ): F[Unit] =
    val trading: F[Unit] =
      Ref.of[F, TradingStatus](TradingStatus.On).flatMap { ref =>
        import TradeCommand.*

        def switch(st: TradingStatus, cmd: TradeCommand): F[Unit] =
          (Time[F].timestamp, GenUUID[F].make[CommandId]).tupled.flatMap { (ts, cmdId) =>
            val uniqueCmd = _CommandId.replace(cmdId).andThen(_CreatedAt.replace(ts))(cmd)
            Logger[F].warn(s">>> Trading $st <<<") *>
              trProducer.send(uniqueCmd) *> ref.set(On) *> Temporal[F].sleep(1.second)
          }

        tradeCommandListGen.replicateA(2).flatten.traverse_ {
          case cmd @ TradeCommand.Start(_, _, _) =>
            switch(On, cmd)
          case cmd @ TradeCommand.Stop(_, _, _) =>
            switch(Off, cmd)
          case cmd =>
            (ref.get, Time[F].timestamp, GenUUID[F].make[CommandId]).tupled.flatMap {
              case (On, ts, cmdId) =>
                val uniqueCmd = _CommandId.replace(cmdId).andThen(_CreatedAt.replace(ts))(cmd)
                Logger[F].info(cmd.show) *> trProducer.send(uniqueCmd) *> Temporal[F].sleep(300.millis)
              case (Off, _, _) =>
                ().pure[F]
            }
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
