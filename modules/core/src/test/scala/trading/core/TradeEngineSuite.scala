package trading.core

import java.time.Instant
import java.util.UUID

import trading.commands.TradeCommand
import trading.core.TradeEngine.fsm
import trading.domain.TradingStatus.*
import trading.domain.*
import trading.state.*

import cats.data.NonEmptyList
import weaver.FunSuite
import weaver.scalacheck.Checkers
import trading.events.TradeEvent

object TradeEngineSuite extends FunSuite with Checkers:
  val id  = CommandId(UUID.randomUUID())
  val eid = EventId(UUID.randomUUID())
  val cid = CorrelationId(UUID.randomUUID())
  val s   = Symbol("EURUSD")
  val ts  = Timestamp(Instant.parse("2021-09-16T14:00:00.00Z"))

  val p1 = Price(1.1987)
  val q1 = Quantity(10)

  val p2 = Price(3.5782)
  val q2 = Quantity(20)

  test("Trade engine fsm") {
    val cmd1       = TradeCommand.Create(id, cid, s, TradeAction.Ask, p1, q1, "test", ts)
    val (st1, ev1) = fsm.run(TradeState.empty, cmd1)
    val xst1       = TradeState(On, Map(s -> Prices(ask = Map(p1 -> q1), bid = Map.empty, p1, p1)))
    val xev1       = TradeEvent.CommandExecuted(eid, cid, cmd1, ts)

    val cmd2       = TradeCommand.Update(id, cid, s, TradeAction.Ask, p2, q2, "test", ts)
    val (st2, ev2) = fsm.run(st1, cmd2)
    val xst2       = TradeState(On, Map(s -> Prices(ask = Map(p1 -> q1, p2 -> q2), bid = Map.empty, p2, p1)))
    val xev2       = TradeEvent.CommandExecuted(eid, cid, cmd2, ts)

    val cmd3       = TradeCommand.Delete(id, cid, s, TradeAction.Ask, p1, "test", ts)
    val (st3, ev3) = fsm.run(st2, cmd3)
    val xst3       = TradeState(On, Map(s -> Prices(ask = Map(p2 -> q2), bid = Map.empty, p2, p1)))
    val xev3       = TradeEvent.CommandExecuted(eid, cid, cmd3, ts)

    val cmd4       = TradeCommand.Create(id, cid, s, TradeAction.Bid, p1, q1, "test", ts)
    val (st4, ev4) = fsm.run(st3, cmd4)
    val xst4       = TradeState(On, Map(s -> Prices(ask = Map(p2 -> q2), bid = Map(p1 -> q1), p2, p1)))
    val xev4       = TradeEvent.CommandExecuted(eid, cid, cmd4, ts)

    val cmd5       = TradeCommand.Stop(id, cid, ts)
    val (st5, ev5) = fsm.run(st4, cmd5)
    val xst5       = TradeState(Off, xst4.prices)
    val xev5       = TradeEvent.Stopped(eid, cid, ts)

    val cmd6       = TradeCommand.Create(id, cid, s, TradeAction.Bid, p1, q1, "test", ts)
    val (st6, ev6) = fsm.run(st5, cmd6)
    val xst6       = xst5
    val xev6       = TradeEvent.CommandRejected(eid, cid, cmd6, Reason("Trading is Off"), ts)

    NonEmptyList
      .of(
        expect.same(st1, xst1),
        expect.same(st2, xst2),
        expect.same(st3, xst3),
        expect.same(st4, xst4),
        expect.same(st5, xst5),
        expect.same(st6, xst6),
        expect.same(ev1(eid, ts), xev1),
        expect.same(ev2(eid, ts), xev2),
        expect.same(ev3(eid, ts), xev3),
        expect.same(ev4(eid, ts), xev4),
        expect.same(ev5(eid, ts), xev5),
        expect.same(ev6(eid, ts), xev6)
      )
      .reduce
  }
