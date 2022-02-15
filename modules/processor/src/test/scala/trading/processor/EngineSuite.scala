package trading.processor

import java.time.Instant
import java.util.UUID

import trading.commands.TradeCommand
import trading.core.dedup.DedupRegistry
import trading.domain.TradingStatus.*
import trading.domain.*
import trading.events.TradeEvent
import trading.lib.*
import trading.lib.Logger.NoOp.given
import trading.state.*

import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.all.*
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

object EngineSuite extends SimpleIOSuite with Checkers:
  val id  = CommandId(UUID.randomUUID())
  val cid = CorrelationId(UUID.randomUUID())
  val s   = Symbol.EURUSD
  val ts  = Timestamp(Instant.parse("2021-09-16T14:00:00.00Z"))

  val p1 = Price(1.1987)
  val q1 = Quantity(10)

  val p2 = Price(3.5782)
  val q2 = Quantity(20)

  // TODO: We should be able to test with different times as well
  given Time[IO] with
    def timestamp: IO[Timestamp] = IO.pure(ts)

  val registry: DedupRegistry[IO] = new:
    def get: IO[DedupState]               = IO.pure(DedupState.empty)
    def save(state: DedupState): IO[Unit] = IO.unit

  test("Processor engine fsm") {
    for
      evts <- IO.ref(none[TradeEvent])
      acks <- IO.ref(none[Consumer.MsgId])
      prod = Producer.test(evts)
      fsm  = Engine.fsm(prod, registry, i => acks.set(i.some))
      (tst1, dst1) <- fsm.runS(
        TradeState.empty -> DedupState.empty,
        Consumer.Msg("id1", Map.empty, TradeCommand.Create(id, cid, s, TradeAction.Ask, p1, q1, "test", ts))
      )
      e1 <- evts.get
      a1 <- acks.get
      tex1 = TradeState(On, Map(s -> Prices(ask = Map(p1 -> q1), bid = Map.empty, p1, p1)))
      dex1 = DedupState(Set(IdRegistry(id, ts)))
      res1 = NonEmptyList
        .of(
          expect.same(tst1, tex1),
          expect.same(dst1, dex1),
          expect.same(e1.size, 1),
          expect.same(a1.size, 1)
        )
        .reduce
    yield res1
  }
