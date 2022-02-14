package trading.trace
package fsm

import java.time.Instant

import trading.commands.*
import trading.domain.*
import trading.domain.generators.*
import trading.events.*
import trading.lib.*

import cats.Monad
import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.kernel.Ref
import natchez.Kernel
import weaver.{ Expectations, SimpleIOSuite }
import weaver.scalacheck.Checkers

object TradingFSMSuite extends SimpleIOSuite with Checkers:

  def mkTracer(
      ref1: Ref[IO, Map[CorrelationId, (TradeCommand, TradeEvent)]],
      ref2: Ref[IO, Map[CorrelationId, (TradeEvent, Alert)]]
  ): Tracer[IO] = new:
    def trading1(cmd: TradeCommand, evt: TradeEvent): IO[Kernel] =
      ref1.update(_.updated(cmd.cid, cmd -> evt)).as(Kernel(Map.empty))
    def trading2(k: Kernel, evt: TradeEvent, alt: Alert): IO[Unit] =
      ref2.update(_.updated(evt.cid, evt -> alt))
    def forecasting(cmd: ForecastCommand, evt: Either[AuthorEvent, ForecastEvent]): IO[Unit] =
      IO.unit

  val gen =
    for
      cid <- correlationIdGen
      cmd <- tradeCommandGen.map(TradeCommand._CorrelationId.replace(cid))
      evt <- genCommandExecEvt.map(TradeEvent._CorrelationId.replace(cid))
      alt <- tradeAlertGen.map(Alert._CorrelationId.replace(cid))
    yield (cid, cmd, evt, alt)

  test("Process messages of same CorrelationId arriving in order") {
    val _gen =
      for
        x <- gen
        y <- gen
      yield x -> y

    forall(_gen) { case ((cid1, cmd1, evt1, alt1), (cid2, cmd2, evt2, alt2)) =>
      for
        ref1 <- IO.ref(Map.empty[CorrelationId, (TradeCommand, TradeEvent)])
        ref2 <- IO.ref(Map.empty[CorrelationId, (TradeEvent, Alert)])
        fsm = tradingFsm(mkTracer(ref1, ref2))
        st1  <- fsm.runS(TradeState.empty, cmd1)
        res1 <- ref1.get
        st2  <- fsm.runS(st1, evt2)
        res2 <- ref1.get
        st3  <- fsm.runS(st2, evt1)
        res3 <- ref1.get
        res4 <- ref2.get
        st4  <- fsm.runS(st3, cmd2)
        res5 <- ref1.get
        _    <- fsm.runS(st4, alt1)
        res6 <- ref2.get
      yield NonEmptyList
        .of(
          expect.same(res1, Map.empty),
          expect.same(res2, Map.empty),
          expect.same(res3, Map(cid1 -> (cmd1, evt1))),
          expect.same(res4, Map.empty),
          expect.same(res5, Map(cid1 -> (cmd1, evt1), cid2 -> (cmd2, evt2))),
          expect.same(res6, Map(cid1 -> (evt1, alt1)))
        )
        .reduce
    }
  }

  test("Process messages of same CorrelationId arriving unordered") {
    forall(gen) { (cid, cmd, evt, alt) =>
      for
        ref1 <- IO.ref(Map.empty[CorrelationId, (TradeCommand, TradeEvent)])
        ref2 <- IO.ref(Map.empty[CorrelationId, (TradeEvent, Alert)])
        fsm = tradingFsm(mkTracer(ref1, ref2))
        st1  <- fsm.runS(TradeState.empty, evt)
        res1 <- ref2.get
        // alt not processed as there is no kernel yet
        st2  <- fsm.runS(st1, alt)
        res2 <- ref2.get
        res3 <- ref1.get
        // command arrives, kernel generated
        _    <- fsm.runS(st2, cmd)
        res4 <- ref1.get
        res5 <- ref2.get
      yield NonEmptyList
        .of(
          expect.same(res1, Map.empty),
          expect.same(res2, Map.empty),
          expect.same(res3, Map.empty),
          expect.same(res4, Map(cid -> (cmd, evt))),
          expect.same(res5, Map(cid -> (evt, alt)))
        )
        .reduce
    }
  }

  object ExpiredTimer extends Time[IO]:
    def timestamp: IO[Timestamp] = IO(Instant.now()).map { now =>
      Timestamp(now.plusSeconds(MatchingIdsExpiration.toSeconds))
    }

  test("Expire messages without matching CorrelationId after configured time") {
    forall(gen) { (cid, cmd, evt, alt) =>
      for
        ref1 <- IO.ref(Map.empty[CorrelationId, (TradeCommand, TradeEvent)])
        ref2 <- IO.ref(Map.empty[CorrelationId, (TradeEvent, Alert)])
        fsm1 = tradingFsm(mkTracer(ref1, ref2))
        st1  <- fsm1.runS(TradeState.empty, cmd)
        res1 <- ref1.get
        st2  <- fsm1.runS(st1, evt)
        res2 <- ref1.get
        res3 <- ref2.get
        fsm2 = tradingFsm[IO](mkTracer(ref1, ref2))(using Logger[IO], Monad[IO], ExpiredTimer)
        st3  <- fsm2.runS(st2, evt)
        res4 <- ref2.get
      yield NonEmptyList
        .of(
          expect.same(res1, Map.empty),
          expect.same(res2, Map(cid -> (cmd, evt))),
          expect.same(res3, Map.empty),
          expect.same(res4, Map.empty),
          expect(st2._3.get(cid).nonEmpty),
          expect(st3._3.get(cid).isEmpty)
        )
        .reduce
    }
  }
