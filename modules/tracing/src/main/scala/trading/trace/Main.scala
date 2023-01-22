package trading.trace

import scala.concurrent.duration.*

import trading.commands.*
import trading.core.AppTopic
import trading.core.http.Ember
import trading.core.snapshots.SnapshotReader
import trading.domain.Alert
import trading.events.*
import trading.lib.{ *, given }
import trading.trace.fsm.*
import trading.trace.tracer.*

import cats.effect.*
import dev.profunktor.pulsar.{ Pulsar, Subscription }
import fs2.Stream

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap {
        (
            server,
            alerts,
            tradingEvents,
            tradingCommands,
            authorEvents,
            forecastEvents,
            forecastCommands,
            fcFsm,
            tdFsm
        ) =>
          val ticks: Stream[IO, TradeIn] =
            Stream.fixedDelay[IO](2.seconds)

          val trading =
            tradingCommands
              .merge[IO, TradeIn](tradingEvents.merge(alerts))
              .merge(ticks)
              .evalMapAccumulate(TradeState.empty)(tdFsm.run)

          val forecasting =
            authorEvents
              .merge[IO, ForecastIn](forecastEvents.merge(forecastCommands))
              .evalMapAccumulate(ForecastState.empty)(fcFsm.run)

          Stream(
            Stream.eval(server.useForever),
            trading,
            forecasting
          ).parJoin(3)
      }
      .compile
      .drain

  val sub =
    Subscription.Builder
      .withName("tracing")
      .withType(Subscription.Type.Exclusive)
      .build

  def resources =
    for
      config <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.make[IO](config.pulsar.url)
      _      <- Resource.eval(Logger[IO].info("Initializing tracing service"))
      ep     <- Honeycomb.makeEntryPoint(config.honeycombApiKey)
      alertsTopic      = AppTopic.Alerts.make(config.pulsar)
      tradingEvtTopic  = AppTopic.TradingEvents.make(config.pulsar)
      tradingCmdTopic  = AppTopic.TradingCommands.make(config.pulsar)
      forecastCmdTopic = AppTopic.ForecastCommands.make(config.pulsar)
      authorEvtTopic   = AppTopic.AuthorEvents.make(config.pulsar)
      forecastEvtTopic = AppTopic.ForecastEvents.make(config.pulsar)
      alerts           <- Consumer.pulsar[IO, Alert](pulsar, alertsTopic, sub).map(_.receive)
      tradingEvents    <- Consumer.pulsar[IO, TradeEvent](pulsar, tradingEvtTopic, sub).map(_.receive)
      tradingCommands  <- Consumer.pulsar[IO, TradeCommand](pulsar, tradingCmdTopic, sub).map(_.receive)
      authorEvents     <- Consumer.pulsar[IO, AuthorEvent](pulsar, authorEvtTopic, sub).map(_.receive)
      forecastEvents   <- Consumer.pulsar[IO, ForecastEvent](pulsar, forecastEvtTopic, sub).map(_.receive)
      forecastCommands <- Consumer.pulsar[IO, ForecastCommand](pulsar, forecastCmdTopic, sub).map(_.receive)
      fcFsm  = forecastFsm[IO].apply(ForecastingTracer.make[IO](ep))
      tdFsm  = tradingFsm[IO].apply(TradingTracer.make[IO](ep))
      server = Ember.default[IO](config.httpPort)
    yield (
      server,
      alerts,
      tradingEvents,
      tradingCommands,
      authorEvents,
      forecastEvents,
      forecastCommands,
      fcFsm,
      tdFsm
    )
