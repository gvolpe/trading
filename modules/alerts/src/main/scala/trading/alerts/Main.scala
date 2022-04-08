package trading.alerts

import trading.core.AppTopic
import trading.core.http.Ember
import trading.core.snapshots.SnapshotReader
import trading.domain.Alert
import trading.events.*
import trading.lib.{ given, * }
import trading.state.TradeState

import cats.effect.*
import cats.syntax.all.*
import dev.profunktor.pulsar.{
  Consumer as PulsarConsumer,
  Producer as PulsarProducer,
  Pulsar,
  SeqIdMaker,
  Subscription
}
import fs2.Stream

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { (server, trConsumer, swConsumer, snapshots, fsm) =>
        Stream.eval(server.useForever).concurrently {
          Stream.eval(snapshots.latest).flatMap {
            case Some(st, id) =>
              trConsumer.receiveM(id).either(swConsumer.receiveM).evalMapAccumulate(st)(fsm.run)
            case None =>
              trConsumer.receiveM.either(swConsumer.receiveM).evalMapAccumulate(TradeState.empty)(fsm.run)
          }
        }
      }
      .compile
      .drain

  // Even though events are sharded by symbol or status, using KeyShared in alerts can be
  // problematic when an instance goes does, due to consumers rebalance.
  val sub =
    Subscription.Builder
      .withName("alerts")
      .withType(Subscription.Type.Failover)
      .build

  // Alert producer settings, dedup and partitioned (for topic compaction in WS)
  val pSettings =
    PulsarProducer
      .Settings[IO, Alert]()
      .withDeduplication(SeqIdMaker.fromEq)
      .withMessageKey(Partition[Alert].key)
      .some

  val compact =
    PulsarConsumer.Settings[IO, SwitchEvent]().withReadCompacted.some

  def resources =
    for
      config <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.make[IO](config.pulsar.url)
      _      <- Resource.eval(Logger[IO].info("Initializing alerts service"))
      alertsTopic  = AppTopic.Alerts.make(config.pulsar)
      switchTopic  = AppTopic.SwitchEvents.make(config.pulsar)
      tradingTopic = AppTopic.TradingEvents.make(config.pulsar)
      snapshots  <- SnapshotReader.make[IO](config.redisUri)
      producer   <- Producer.pulsar[IO, Alert](pulsar, alertsTopic, pSettings)
      trConsumer <- Consumer.pulsar[IO, TradeEvent](pulsar, tradingTopic, sub)
      swConsumer <- Consumer.pulsar[IO, SwitchEvent](pulsar, switchTopic, sub, compact)
      engine = Engine.fsm(producer, trConsumer, swConsumer)
      server = Ember.default[IO](config.httpPort)
    yield (server, trConsumer, swConsumer, snapshots, engine)
