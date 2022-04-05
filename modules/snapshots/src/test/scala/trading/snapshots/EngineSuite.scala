package trading.snapshots

import java.util.UUID

import trading.commands.TradeCommand
import trading.core.snapshots.SnapshotWriter
import trading.domain.*
import trading.domain.generators.*
import trading.events.TradeEvent
import trading.lib.*
import trading.lib.Consumer.{ Msg, MsgId }
import trading.lib.Logger.NoOp.given
import trading.state.*

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.all.*
import fs2.Stream
import org.scalacheck.Gen
import weaver.{ Expectations, SimpleIOSuite }
import weaver.scalacheck.Checkers

object EngineSuite extends SimpleIOSuite with Checkers:
  def succesfulWriter(ref: Ref[IO, Option[TradeState]]): SnapshotWriter[IO] = new:
    def save(state: TradeState, id: Consumer.MsgId): IO[Unit] = ref.set(state.some)

  val failingWriter: SnapshotWriter[IO] = new:
    def save(state: TradeState, id: Consumer.MsgId): IO[Unit] = IO.raiseError(new Exception("boom"))

  def mkAcker(acks: Ref[IO, List[MsgId]]): Acker[IO, TradeEvent] = new:
    def ack(id: MsgId): IO[Unit]                   = acks.update(_ :+ id)
    def ack(ids: Set[MsgId]): IO[Unit]             = acks.update(_ ::: ids.toList)
    def ack(id: Consumer.MsgId, tx: Txn): IO[Unit] = ack(id)
    def nack(id: MsgId): IO[Unit]                  = IO.unit

  val msgId: MsgId = UUID.randomUUID().toString

  val tick: Tick = ()

  def baseTest(
      gen: Gen[TradeEvent],
      mkWriter: Ref[IO, Option[TradeState]] => SnapshotWriter[IO],
      expWrites: TradeState => Option[TradeState],
      expAcks: List[MsgId]
  ): IO[Expectations] =
    forall(gen) { evt =>
      for
        writes <- IO.ref(none[TradeState])
        acks   <- IO.ref(List.empty[MsgId])
        fsm = Engine.fsm[IO](mkAcker(acks), mkWriter(writes))
        nst1 <- fsm.runS(TradeState.empty -> List.empty, Msg(msgId, Map.empty, evt))
        res1 <- writes.get
        nst2 <- fsm.runS(nst1, tick)
        res2 <- writes.get
        res3 <- acks.get
      yield NonEmptyList
        .of(
          expect.same(res1, None),
          expect.same(res2, expWrites(nst2._1)),
          expect.same(res3, expAcks)
        )
        .reduce
    }

  // Should ack AND write the new trade state
  test("forecast fsm with command executed events") {
    baseTest(
      gen = genCommandExecEvt,
      mkWriter = succesfulWriter,
      expWrites = nst => Some(nst),
      expAcks = List(msgId)
    )
  }

  // Should ack without writing new state
  test("forecast fsm with other events") {
    baseTest(
      gen = genTradeEventNoCmdExec,
      mkWriter = succesfulWriter,
      expWrites = _ => None,
      expAcks = List(msgId)
    )
  }

  // Should Nack without writing new state
  test("forecast fsm with failing snapshot writer") {
    baseTest(
      gen = genCommandExecEvt,
      mkWriter = _ => failingWriter,
      expWrites = _ => None,
      expAcks = List.empty
    )
  }
