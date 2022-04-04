package trading.processor

import java.time.Instant
import java.util.UUID

import trading.commands.TradeCommand
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

  test("Processor engine fsm") {
    for
      evts <- IO.ref(List.empty[TradeEvent])
      swts <- IO.ref(List.empty[TradeEvent.Switch])
      acks <- IO.ref(List.empty[Consumer.MsgId])
      prod     = Producer.testMany(evts)
      switcher = Producer.testMany(swts)
      fsm      = Engine.fsm(prod, switcher, i => acks.update(_ :+ i))
      // first command: Create
      tst1 <- fsm.runS(
        TradeState.empty,
        Consumer.Msg("id1", Map.empty, TradeCommand.Create(id, cid, s, TradeAction.Ask, p1, q1, "test", ts))
      )
      tex1 = TradeState(On, Map(s -> Prices(ask = Map(p1 -> q1), bid = Map.empty, p1, p1)))
      e1 <- evts.get
      a1 <- acks.get
      s1 <- swts.get
      // second command: Stop
      tst2 <- fsm.runS(
        tst1,
        Consumer.Msg("id2", Map.empty, TradeCommand.Stop(id, cid, ts))
      )
      tex2 = TradeState(Off, tex1.prices)
      e2 <- evts.get
      a2 <- acks.get
      s2 <- swts.get
    yield NonEmptyList
      .of(
        expect.same(tst1, tex1),
        expect.same(e1.size, 1),
        expect.same(a1, List("id1")),
        expect.same(s1.size, 0),
        expect.same(tst2, tex2),
        expect.same(e2.size, 2),
        expect.same(a2, List("id1", "id2")),
        expect.same(s2.size, 1)
      )
      .reduce

  }
