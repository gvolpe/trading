package trading.processor

import java.time.Instant
import java.util.UUID

import trading.commands.*
import trading.domain.TradingStatus.*
import trading.domain.*
import trading.events.*
import trading.lib.*
import trading.lib.Consumer.{ Msg, MsgId }
import trading.lib.Logger.NoOp.given
import trading.state.*

import cats.data.NonEmptyList
import cats.effect.{ IO, Ref }
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

  def mkAcker[A](ref: Ref[IO, List[Consumer.MsgId]]): Acker[IO, A] = new:
    def ack(id: Consumer.MsgId): IO[Unit]          = ref.update(_ :+ id)
    def ack(ids: Set[Consumer.MsgId]): IO[Unit]    = ref.update(_ ++ ids.toList)
    def ack(id: Consumer.MsgId, tx: Txn): IO[Unit] = ack(id)
    def nack(id: Consumer.MsgId): IO[Unit]         = IO.unit

  test("Processor engine fsm") {
    for
      evts <- IO.ref(List.empty[TradeEvent])
      swts <- IO.ref(List.empty[SwitchEvent])
      acks <- IO.ref(List.empty[Consumer.MsgId])
      prod     = Producer.testMany(evts)
      switcher = Producer.testMany(swts)
      fsm      = Engine.fsm(prod, switcher, Txn.dummy, mkAcker(acks), mkAcker(acks))
      // first command: Create
      tst1 <- fsm.runS(
        TradeState.empty,
        Msg(MsgId.earliest, Map.empty, TradeCommand.Create(id, cid, s, TradeAction.Ask, p1, q1, "test", ts)).asLeft
      )
      tex1 = TradeState(On, Map(s -> Prices(ask = Map(p1 -> q1), bid = Map.empty, p1, p1)))
      e1 <- evts.get
      a1 <- acks.get
      s1 <- swts.get
      // second command: Stop
      tst2 <- fsm.runS(
        tst1,
        Msg(MsgId.latest, Map.empty, SwitchCommand.Stop(id, cid, ts)).asRight
      )
      tex2 = TradeState(Off, tex1.prices)
      e2 <- evts.get
      a2 <- acks.get
      s2 <- swts.get
    yield NonEmptyList
      .of(
        expect.same(tst1, tex1),
        expect.same(e1.size, 1),
        expect.same(a1, List(MsgId.earliest)),
        expect.same(s1.size, 0),
        expect.same(tst2, tex2),
        expect.same(e2.size, 1),
        expect.same(a2, List(MsgId.earliest, MsgId.latest)),
        expect.same(s2.size, 1)
      )
      .reduce

  }
