package trading.forecasts

import java.time.Instant
import java.util.UUID

import trading.domain.*
import trading.events.*
import trading.forecasts.store.*
import trading.lib.*
import trading.lib.Consumer.{ Msg, MsgId }
import trading.lib.Logger.NoOp.given

import cats.effect.IO
import cats.effect.kernel.{ Ref, Resource }
import cats.syntax.all.*
import weaver.{ Expectations, SimpleIOSuite }
import weaver.scalacheck.Checkers

object VotesHandlerSuite extends SimpleIOSuite with Checkers:
  val msgId: MsgId = MsgId.latest

  extension (evt: ForecastEvent) def asMsg: Consumer.Msg[ForecastEvent] = Consumer.Msg(msgId, Map.empty, evt)

  def genId = UUID.randomUUID()

  val eid = EventId(genId)
  val cid = CorrelationId(genId)
  val fid = ForecastId(genId)
  val ts  = Timestamp(Instant.parse("2021-09-16T14:00:00.00Z"))

  class DummyAcker extends Acker[IO, ForecastEvent]:
    def ack(id: Consumer.MsgId): IO[Unit]          = IO.unit
    def ack(ids: Set[Consumer.MsgId]): IO[Unit]    = IO.unit
    def ack(id: Consumer.MsgId, tx: Txn): IO[Unit] = ack(id)
    def nack(id: Consumer.MsgId): IO[Unit]         = IO.unit

  def mkNAcker(ref: Ref[IO, Option[Consumer.MsgId]]): Acker[IO, ForecastEvent] = new DummyAcker:
    override def nack(id: Consumer.MsgId): IO[Unit] = ref.set(id.some)

  class DummyForecastStore extends ForecastStore[IO]:
    def fetch(fid: ForecastId): IO[Option[Forecast]] = IO.none
    def tx: Resource[IO, TxForecastStore[IO]]        = Resource.pure(new DummyTxForecastStore)

  class DummyTxForecastStore extends TxForecastStore[IO]:
    def save(aid: AuthorId, fc: Forecast): IO[Unit]          = IO.unit
    def castVote(fid: ForecastId, res: VoteResult): IO[Unit] = IO.unit
    def registerVote(evt: ForecastEvent.Voted): IO[Unit]     = IO.unit
    def outbox(evt: ForecastEvent): IO[Unit]                 = IO.unit

  def okForecastStore(
      events: Ref[IO, Option[ForecastEvent]],
      votes: Ref[IO, Option[(ForecastId, VoteResult)]]
  ): ForecastStore[IO] = new DummyForecastStore:
    override def tx: Resource[IO, TxForecastStore[IO]] = Resource.pure {
      new DummyTxForecastStore:
        override def castVote(fid: ForecastId, res: VoteResult): IO[Unit] = votes.set(Some(fid -> res))
        override def registerVote(evt: ForecastEvent.Voted): IO[Unit]     = events.set(evt.some)
    }

  def failForecastStore(
      events: Ref[IO, Option[ForecastEvent]],
      error: Throwable
  ): ForecastStore[IO] = new DummyForecastStore:
    override def tx: Resource[IO, TxForecastStore[IO]] = Resource
      .pure {
        new DummyTxForecastStore:
          override def castVote(fid: ForecastId, res: VoteResult): IO[Unit] = IO.raiseError(error)
          override def registerVote(evt: ForecastEvent.Voted): IO[Unit]     = events.set(evt.some)
      }
      .onFinalizeCase {
        case Resource.ExitCase.Errored(_) => events.set(None) // simulates a db roll-back
        case _                            => IO.unit
      }

  test("Successfully register a vote in the database") {
    for
      fc <- IO.ref(none[ForecastEvent])
      vt <- IO.ref(none[(ForecastId, VoteResult)])
      h   = VotesHandler.make(okForecastStore(fc, vt), DummyAcker())
      evt = ForecastEvent.Voted(eid, cid, fid, VoteResult.Up, ts)
      _    <- h.run(evt.asMsg)
      res1 <- fc.get
      res2 <- vt.get
    yield expect.same(Some(evt), res1) && expect.same(Some(fid -> VoteResult.Up), res2)
  }

  test("Failing to cast a vote fails the entire transaction and should NACK the event") {
    for
      fc <- IO.ref(none[ForecastEvent])
      ck <- IO.ref(none[Consumer.MsgId])
      h   = VotesHandler.make(failForecastStore(fc, new Exception("boom")), mkNAcker(ck))
      evt = ForecastEvent.Voted(eid, cid, fid, VoteResult.Up, ts)
      _    <- h.run(evt.asMsg)
      res1 <- fc.get
      res2 <- ck.get
    yield expect(res1.isEmpty) && expect.same(Some(msgId), res2)
  }

  test("Failing to register a vote because of duplicate EventId should be ignored and ACKed") {
    for
      fc <- IO.ref(none[ForecastEvent])
      ck <- IO.ref(none[Consumer.MsgId])
      h   = VotesHandler.make(failForecastStore(fc, DuplicateEventId(eid)), mkNAcker(ck))
      evt = ForecastEvent.Voted(eid, cid, fid, VoteResult.Up, ts)
      _    <- h.run(evt.asMsg)
      res1 <- fc.get
      res2 <- ck.get
    yield expect(res1.isEmpty) && expect(res2.isEmpty)
  }
