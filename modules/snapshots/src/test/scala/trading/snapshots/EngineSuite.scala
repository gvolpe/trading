package trading.snapshots

import trading.core.snapshots.SnapshotWriter
import trading.domain.*
import trading.domain.generators.*
import trading.events.*
import trading.lib.*
import trading.lib.Consumer.{ Msg, MsgId }
import trading.lib.Logger.NoOp.given
import trading.state.*

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.all.*
import org.scalacheck.Gen
import weaver.{ Expectations, SimpleIOSuite }
import weaver.scalacheck.Checkers

object EngineSuite extends SimpleIOSuite with Checkers:
  def succesfulWriter(ref: Ref[IO, Option[TradeState]]): SnapshotWriter[IO] = new:
    def save(state: TradeState, id: Consumer.MsgId): IO[Unit] = ref.set(state.some)
    def saveStatus(st: TradingStatus): IO[Unit]               = ref.update(_.map(_.copy(status = st)))

  val failingWriter: SnapshotWriter[IO] = new:
    def save(state: TradeState, id: Consumer.MsgId): IO[Unit] = IO.raiseError(new Exception("boom"))
    def saveStatus(st: TradingStatus): IO[Unit]               = IO.unit

  def mkAcker[A](acks: Ref[IO, List[MsgId]]): Acker[IO, A] = new:
    def ack(id: MsgId): IO[Unit]                   = acks.update(_ :+ id)
    def ack(ids: Set[MsgId]): IO[Unit]             = acks.update(_ ::: ids.toList)
    def ack(id: Consumer.MsgId, tx: Txn): IO[Unit] = ack(id)
    def nack(id: MsgId): IO[Unit]                  = IO.unit

  val msgId: MsgId = MsgId.latest

  val tick: Tick = ()

  def baseTest(
      gen: Gen[Either[TradeEvent, SwitchEvent]],
      mkWriter: Ref[IO, Option[TradeState]] => SnapshotWriter[IO],
      expWrites: TradeState => Option[TradeState],
      expAcks: List[MsgId]
  ): IO[Expectations] =
    forall(gen) { evt =>
      for
        writes <- IO.ref(none[TradeState])
        acks   <- IO.ref(List.empty[MsgId])
        msg = evt.bimap(Msg(msgId, Map.empty, _), Msg(msgId, Map.empty, _))
        fsm = Engine.fsm[IO](mkAcker(acks), mkAcker(acks), mkWriter(writes))
        nst1 <- fsm.runS(TradeState.empty -> List.empty, msg)
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

  test("snapshot fsm with command executed events should ack AND write the new state") {
    baseTest(
      gen = genCommandExecEvt.map(_.asLeft),
      mkWriter = succesfulWriter,
      expWrites = _.some,
      expAcks = List(msgId)
    )
  }

  test("snapshot fsm with other events should ack without writing new state") {
    baseTest(
      gen = genTradeEventNoCmdExec,
      mkWriter = succesfulWriter,
      expWrites = _ => None,
      expAcks = List(msgId)
    )
  }

  test("snapshot fsm with failing snapshot writer should NOT ack") {
    baseTest(
      gen = genCommandExecEvt.map(_.asLeft),
      mkWriter = _ => failingWriter,
      expWrites = _ => None,
      expAcks = List.empty
    )
  }
