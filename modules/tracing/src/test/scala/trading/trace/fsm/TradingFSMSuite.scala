package trading.trace
package fsm

import java.time.Instant

import trading.commands.*
import trading.domain.*
import trading.domain.generators.*
import trading.events.*
import trading.lib.*
import trading.trace.tracer.TradingTracer

import cats.Monad
import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.kernel.Ref
import natchez.Kernel
import weaver.{ Expectations, SimpleIOSuite }
import weaver.scalacheck.Checkers

object TradingFSMSuite extends SimpleIOSuite with Checkers:

  val EmptyKernel    = Kernel(Map.empty)
  val EmptyCmdKernel = CmdKernel(EmptyKernel)
  val EmptyEvtKernel = EvtKernel(EmptyKernel)

  def mkTracer(
      cmds: Ref[IO, Map[CorrelationId, TradeCommand]],
      evts: Ref[IO, Map[CorrelationId, TradeEvent]],
      alts: Ref[IO, Map[CorrelationId, Alert]]
  ): TradingTracer[IO] = new:
    def command(cmd: TradeCommand): IO[CmdKernel] =
      cmds.update(_.updated(cmd.cid, cmd)).as(EmptyCmdKernel)
    def event(kernel: CmdKernel, evt: TradeEvent): IO[EvtKernel] =
      evts.update(_.updated(evt.cid, evt)).as(EmptyEvtKernel)
    def alert(kernel: EvtKernel, alt: Alert): IO[Unit] =
      alts.update(_.updated(alt.cid, alt))

  val gen =
    for
      cid <- correlationIdGen
      cmd <- tradeCommandGen.map(TradeCommand._CorrelationId.replace(cid))
      evt <- genCommandExecEvt.map(TradeEvent._CorrelationId.replace(cid))
      alt <- tradeAlertGen.map(Alert._CorrelationId.replace(cid))
    yield (cid, cmd, evt, alt)

  test("Process messages of same CorrelationId arriving in order") {
    forall(gen) { (cid, cmd, evt, alt) =>
      for
        cmds <- IO.ref(Map.empty[CorrelationId, TradeCommand])
        evts <- IO.ref(Map.empty[CorrelationId, TradeEvent])
        alts <- IO.ref(Map.empty[CorrelationId, Alert])
        fsm = tradingFsm.apply(mkTracer(cmds, evts, alts))
        // Command in should set the cmd kernel
        st1 <- fsm.runS(TradeState.empty, cmd)
        ckl1 = st1._3.get(cid).flatMap(_._2)
        ekl1 = st1._3.get(cid).flatMap(_._3)
        res1 <- cmds.get
        // Event in should set evt kernel
        st2 <- fsm.runS(st1, evt)
        ckl2 = st2._3.get(cid).flatMap(_._2)
        ekl2 = st2._3.get(cid).flatMap(_._3)
        res2 <- evts.get
        // Tick should not have any effect
        st3  <- fsm.runS(st2, ())
        res3 <- cmds.get
        res4 <- evts.get
        // Alert should be processed straight away
        _    <- fsm.runS(st3, alt)
        res5 <- alts.get
      yield NonEmptyList
        .of(
          expect.same(ckl1, Some(EmptyKernel)),
          expect.same(ekl1, None),
          expect.same(res1, Map(cid -> cmd)),
          expect.same(ckl2, Some(EmptyKernel)),
          expect.same(ekl2, Some(EmptyKernel)),
          expect.same(res2, Map(cid -> evt)),
          expect.same(res3, res1),
          expect.same(res4, res2),
          expect.same(res5, Map(cid -> alt))
        )
        .reduce
    }
  }

  test("Process messages of same CorrelationId arriving unordered") {
    val _gen =
      for
        x <- gen
        y <- gen
      yield x -> y

    forall(_gen) { case ((cid1, cmd1, evt1, alt1), (cid2, cmd2, evt2, alt2)) =>
      for
        cmds <- IO.ref(Map.empty[CorrelationId, TradeCommand])
        evts <- IO.ref(Map.empty[CorrelationId, TradeEvent])
        alts <- IO.ref(Map.empty[CorrelationId, Alert])
        fsm = tradingFsm.apply(mkTracer(cmds, evts, alts))
        // First command in should set the cmd kernel
        st1 <- fsm.runS(TradeState.empty, cmd1)
        ckl1 = st1._3.get(cid1).flatMap(_._2)
        ekl1 = st1._3.get(cid1).flatMap(_._3)
        res1 <- cmds.get
        // Second event in should be added to the queue as there is no cmd kernel yet
        st2 <- fsm.runS(st1, evt2)
        ckl2 = st2._3.get(cid2).flatMap(_._2)
        ekl2 = st2._3.get(cid2).flatMap(_._3)
        res2 <- evts.get
        // First event in should set evt kernel
        st3 <- fsm.runS(st2, evt1)
        ckl3 = st3._3.get(cid1).flatMap(_._2)
        ekl3 = st3._3.get(cid1).flatMap(_._3)
        res3 <- evts.get
        // Tick should not have any effect yet
        st4  <- fsm.runS(st3, ())
        res4 <- cmds.get
        res5 <- evts.get
        // Second command in should set the cmd kernel
        st5 <- fsm.runS(st4, cmd2)
        ckl4 = st5._3.get(cid2).flatMap(_._2)
        ekl4 = st5._3.get(cid2).flatMap(_._3)
        res6 <- cmds.get
        // Tick should now process evt2
        st6  <- fsm.runS(st5, ())
        res7 <- cmds.get
        res8 <- evts.get
        // First alert should be processed straight away
        _    <- fsm.runS(st6, alt1)
        res9 <- alts.get
      yield NonEmptyList
        .of(
          expect.same(ckl1, Some(EmptyKernel)),
          expect.same(ekl1, None),
          expect.same(res1, Map(cid1 -> cmd1)),
          expect.same(ekl2, None),
          expect.same(ckl2, None),
          expect.same(res2, Map.empty),
          expect.same(ckl3, Some(EmptyKernel)),
          expect.same(ekl3, Some(EmptyKernel)),
          expect.same(res3, Map(cid1 -> evt1)),
          expect.same(res4, res1),
          expect.same(res5, res3),
          expect.same(ckl4, Some(EmptyKernel)),
          expect.same(ekl4, None),
          expect.same(res6, Map(cid1 -> cmd1, cid2 -> cmd2)),
          expect.same(res7, res6),
          expect.same(res8, Map(cid1 -> evt1, cid2 -> evt2)),
          expect.same(res9, Map(cid1 -> alt1))
        )
        .reduce
    }
  }

  object ExpiredTimer extends Time[IO]:
    def timestamp: IO[Timestamp] = IO(Instant.now()).map { now =>
      Timestamp(now.plusSeconds(MatchingIdsExpiration.toSeconds))
    }

  test("Expire messages without matching CorrelationId after configured time") {
    val gen =
      for
        cid <- correlationIdGen
        cmd <- tradeCommandGen
        evt <- genCommandExecEvt
        alt <- tradeAlertGen
      yield (cid, cmd, evt, alt)

    forall(gen) { (cid, cmd, evt, alt) =>
      for
        cmds <- IO.ref(Map.empty[CorrelationId, TradeCommand])
        evts <- IO.ref(Map.empty[CorrelationId, TradeEvent])
        alts <- IO.ref(Map.empty[CorrelationId, Alert])
        fsm1 = tradingFsm.apply(mkTracer(cmds, evts, alts))
        // Command should be processed
        st1  <- fsm1.runS(TradeState.empty, cmd)
        res1 <- cmds.get
        // Event should be enqueued waiting for command kernel
        st2  <- fsm1.runS(st1, evt)
        res2 <- evts.get
        fsm2 = tradingFsm[IO](using Logger[IO], Monad[IO], ExpiredTimer)(mkTracer(cmds, evts, alts))
        // Tick should trigger message expiration
        st3 <- fsm2.runS(st2, ())
      yield NonEmptyList
        .of(
          expect.same(res1, Map(cmd.cid -> cmd)),
          expect(st2._3.get(cmd.cid).nonEmpty),
          expect.same(res2, Map.empty),
          expect.same(st3._3, Map.empty)
        )
        .reduce
    }
  }
