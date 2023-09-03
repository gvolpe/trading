package trading.alerts

import trading.core.AppTopic
import trading.core.http.Ember
import trading.core.snapshots.SnapshotReader
import trading.domain.{ Alert, AppId, PriceUpdate }
import trading.events.*
import trading.lib.{ *, given }
import trading.state.TradeState

import cats.effect.*
import cats.syntax.all.*
import dev.profunktor.pulsar.{ Consumer as PulsarConsumer, Producer as PulsarProducer, Pulsar, Subscription }
import fs2.Stream

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { (server, trConsumer, swConsumer, puConsumer, snapshots, fsm) =>
        Stream.eval(server.useForever).concurrently {
          Stream.eval(snapshots.latest).flatMap {
            case Some(st, id) =>
              Stream.eval(IO.deferred[Unit]).flatMap { gate =>
                trConsumer
                  .rewind(id, gate)
                  .either(Stream.exec(gate.get) ++ swConsumer.receiveM)
                  .either(Stream.exec(gate.get) ++ puConsumer.receiveM)
                  .union2
                  .evalMapAccumulate(st)(fsm.run)
              }
            case None =>
              trConsumer.receiveM
                .either(swConsumer.receiveM)
                .either(puConsumer.receiveM)
                .union2
                .evalMapAccumulate(TradeState.empty)(fsm.run)
          }
        }
      }
      .compile
      .drain

  // TradeEvent subscription, sharded by symbol
  def trSub(appId: AppId) =
    Subscription.Builder
      .withName(appId.name)
      .withType(Subscription.Type.KeyShared)
      .build

  // SwitchEvent & PriceUpdate subscription, exclusive per instance
  def swSub(appId: AppId) =
    Subscription.Builder
      .withName(appId.show)
      .withType(Subscription.Type.Exclusive)
      .build

  // Alert producer settings, dedup (retries) and partitioned (for topic compaction in WS)
  val altSettings =
    PulsarProducer
      .Settings[IO, Alert]()
      .withDeduplication
      .withMessageKey(Compaction[Alert].key)
      .some

  // PriceUpdate producer settings, dedup (retries) and partitioned (for topic compaction in WS)
  val priSettings =
    PulsarProducer
      .Settings[IO, PriceUpdate]()
      .withDeduplication
      .withMessageKey(Compaction[PriceUpdate].key)
      .some

  val compact =
    PulsarConsumer.Settings[IO, SwitchEvent]().withReadCompacted.some

  def resources =
    for
      config <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.make[IO](config.pulsar.url, Pulsar.Settings().withTransactions)
      _      <- Resource.eval(Logger[IO].info(s"Initializing service: ${config.appId.show}"))
      alertsTopic  = AppTopic.Alerts.make(config.pulsar)
      pricesTopic  = AppTopic.PriceUpdates.make(config.pulsar)
      switchTopic  = AppTopic.SwitchEvents.make(config.pulsar)
      tradingTopic = AppTopic.TradingEvents.make(config.pulsar)
      snapshots      <- SnapshotReader.make[IO](config.redisUri)
      alertProducer  <- Producer.pulsar[IO, Alert](pulsar, alertsTopic, altSettings)
      pricesProducer <- Producer.pulsar[IO, PriceUpdate](pulsar, pricesTopic, priSettings)
      xSub = swSub(config.appId)
      trConsumer <- Consumer.pulsar[IO, TradeEvent](pulsar, tradingTopic, trSub(config.appId))
      swConsumer <- Consumer.pulsar[IO, SwitchEvent](pulsar, switchTopic, xSub, compact)
      puConsumer <- Consumer.pulsar[IO, PriceUpdate](pulsar, pricesTopic, xSub)
      server = Ember.default[IO](config.httpPort)
    yield (
      server,
      trConsumer,
      swConsumer,
      puConsumer,
      snapshots,
      Engine.fsm(
        config.appId,
        alertProducer,
        pricesProducer,
        Txn.make(pulsar),
        trConsumer,
        swConsumer,
        puConsumer
      )
    )
