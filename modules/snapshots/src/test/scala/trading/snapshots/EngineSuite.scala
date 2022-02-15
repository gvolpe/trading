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
    def save(state: TradeState): IO[Unit] = ref.set(state.some)

  val failingWriter: SnapshotWriter[IO] = new:
    def save(state: TradeState): IO[Unit] = IO.raiseError(new Exception("boom"))

  def mkAcker(
      acks: Ref[IO, Option[MsgId]],
      nacks: Ref[IO, Option[MsgId]]
  ): Acker[IO, TradeEvent] = new:
    def ack(id: MsgId): IO[Unit]  = acks.set(id.some)
    def nack(id: MsgId): IO[Unit] = nacks.set(id.some)

  val msgId: MsgId = UUID.randomUUID().toString

  def baseTest(
      gen: Gen[TradeEvent],
      mkWriter: Ref[IO, Option[TradeState]] => SnapshotWriter[IO],
      expWrites: TradeState => Option[TradeState],
      expAcks: Option[MsgId],
      expNacks: Option[MsgId]
  ): IO[Expectations] =
    forall(gen) { evt =>
      for
        writes <- IO.ref(none[TradeState])
        acks   <- IO.ref(none[MsgId])
        nacks  <- IO.ref(none[MsgId])
        fsm = Engine.fsm[IO](mkAcker(acks, nacks), mkWriter(writes))
        nst  <- fsm.runS(TradeState.empty, Msg(msgId, Map.empty, evt))
        res1 <- writes.get
        res2 <- acks.get
        res3 <- nacks.get
      yield NonEmptyList
        .of(
          expect.same(res1, expWrites(nst)),
          expect.same(res2, expAcks),
          expect.same(res3, expNacks)
        )
        .reduce
    }

  // Should ack AND write the new trade state
  test("forecast fsm with command executed events") {
    baseTest(
      gen = genCommandExecEvt,
      mkWriter = succesfulWriter,
      expWrites = nst => Some(nst),
      expAcks = Some(msgId),
      expNacks = None
    )
  }

  // Should ack without writing new state
  test("forecast fsm with other events") {
    baseTest(
      gen = genTradeEventNoCmdExec,
      mkWriter = succesfulWriter,
      expWrites = _ => None,
      expAcks = Some(msgId),
      expNacks = None
    )
  }

  // Should Nack without writing new state
  test("forecast fsm with failing snapshot writer") {
    baseTest(
      gen = genCommandExecEvt,
      mkWriter = _ => failingWriter,
      expWrites = _ => None,
      expAcks = None,
      expNacks = Some(msgId)
    )
  }
