package trading.forecasts

import java.time.Instant
import java.util.UUID

import trading.IsUUID
import trading.commands.ForecastCommand
import trading.domain.TradingStatus.*
import trading.domain.*
import trading.events.*
import trading.forecasts.store.*
import trading.lib.*
import trading.lib.Consumer.{ Msg, MsgId }
import trading.lib.Logger.NoOp.given
import trading.state.*

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.kernel.{ Ref, Resource }
import cats.syntax.all.*
import weaver.{ Expectations, SimpleIOSuite }
import weaver.scalacheck.Checkers

object EngineSuite extends SimpleIOSuite with Checkers:
  extension (cmd: ForecastCommand) def asMsg: Consumer.Msg[ForecastCommand] = Consumer.Msg(msgId, Map.empty, cmd)

  val id  = CommandId(UUID.randomUUID())
  val cid = CorrelationId(UUID.randomUUID())
  val ts  = Timestamp(Instant.parse("2021-09-16T14:00:00.00Z"))

  val genId = UUID.randomUUID()

  val authorId   = AuthorId(genId)
  val authorName = AuthorName("swilson")

  val fid    = ForecastId(genId)
  val symbol = Symbol.EURUSD
  val desc   = ForecastDescription("test")
  val tag    = ForecastTag.Short

  val eventId = EventId(genId)

  given GenUUID[IO] with
    def make[A: IsUUID]: IO[A] = IO.pure(IsUUID[A].iso.get(genId))

  given Time[IO] with
    def timestamp: IO[Timestamp] = IO.pure(ts)

  class DummyForecastStore extends ForecastStore[IO]:
    def fetch(fid: ForecastId): IO[Option[Forecast]] = IO.none
    def tx: Resource[IO, TxForecastStore[IO]]        = Resource.pure(new DummyTxForecastStore)

  class DummyTxForecastStore extends TxForecastStore[IO]:
    def save(aid: AuthorId, fc: Forecast): IO[Unit]          = IO.unit
    def castVote(fid: ForecastId, res: VoteResult): IO[Unit] = IO.unit
    def registerVote(evt: ForecastEvent.Voted): IO[Unit]     = IO.unit
    def outbox(evt: ForecastEvent): IO[Unit]                 = IO.unit

  class DummyAuthorStore extends AuthorStore[IO]:
    def fetch(id: AuthorId): IO[Option[Author]] = IO.none
    def tx: Resource[IO, TxAuthorStore[IO]]     = Resource.pure(new DummyTxAuthorStore)

  class DummyTxAuthorStore extends TxAuthorStore[IO]:
    def save(author: Author): IO[Unit]     = IO.unit
    def outbox(evt: AuthorEvent): IO[Unit] = IO.unit

  def okAuthorStore(ref: Ref[IO, Option[AuthorEvent]]): AuthorStore[IO] = new DummyAuthorStore:
    override def tx: Resource[IO, TxAuthorStore[IO]] = Resource.pure {
      new DummyTxAuthorStore:
        override def outbox(evt: AuthorEvent): IO[Unit] = ref.set(evt.some)
    }

  def okForecastStore(ref: Ref[IO, Option[ForecastEvent]]): ForecastStore[IO] = new DummyForecastStore:
    override def tx: Resource[IO, TxForecastStore[IO]] = Resource.pure {
      new DummyTxForecastStore:
        override def outbox(evt: ForecastEvent): IO[Unit] = ref.set(evt.some)
    }

  val failAuthorStore: AuthorStore[IO] = new DummyAuthorStore:
    override def tx: Resource[IO, TxAuthorStore[IO]] = Resource.pure {
      new TxAuthorStore[IO]:
        def save(author: Author): IO[Unit]     = IO.raiseError(DuplicateAuthorError)
        def outbox(evt: AuthorEvent): IO[Unit] = IO.unit
    }

  val unexpectedFailAuthorStore: AuthorStore[IO] = new DummyAuthorStore:
    override def tx: Resource[IO, TxAuthorStore[IO]] = Resource.pure {
      new TxAuthorStore[IO]:
        def save(author: Author): IO[Unit]     = IO.raiseError(new Exception("boom"))
        def outbox(evt: AuthorEvent): IO[Unit] = IO.unit
    }

  val failForecastStore: ForecastStore[IO] = new DummyForecastStore:
    override def tx: Resource[IO, TxForecastStore[IO]] = Resource.pure {
      new DummyTxForecastStore:
        override def save(aid: AuthorId, fc: Forecast): IO[Unit] =
          IO.raiseError(AuthorNotFound)
    }

  val voteFailForecastStore: ForecastStore[IO] = new DummyForecastStore:
    override def tx: Resource[IO, TxForecastStore[IO]] = Resource.pure {
      new DummyTxForecastStore:
        override def castVote(fid: ForecastId, res: VoteResult): IO[Unit] =
          IO.raiseError(ForecastNotFound)
    }

  class DummyAcker extends Acker[IO, ForecastCommand]:
    def ack(id: Consumer.MsgId): IO[Unit]          = IO.unit
    def ack(ids: Set[Consumer.MsgId]): IO[Unit]    = IO.unit
    def ack(id: Consumer.MsgId, tx: Txn): IO[Unit] = ack(id)
    def nack(id: Consumer.MsgId): IO[Unit]         = IO.unit

  def mkNAcker(ref: Ref[IO, Option[Consumer.MsgId]]): Acker[IO, ForecastCommand] = new DummyAcker:
    override def nack(id: Consumer.MsgId): IO[Unit] = ref.set(id.some)

  val msgId: MsgId = MsgId.latest

  private def baseTest(
      mkAuthorStore: Ref[IO, Option[AuthorEvent]] => AuthorStore[IO] = okAuthorStore,
      mkForecastStore: Ref[IO, Option[ForecastEvent]] => ForecastStore[IO] = okForecastStore,
      in: ForecastCommand,
      ex1: Option[AuthorEvent] => Expectations,
      ex2: Option[ForecastEvent] => Expectations
  ): IO[Expectations] =
    for
      at <- IO.ref(none[AuthorEvent])
      fc <- IO.ref(none[ForecastEvent])
      p1     = Producer.test(at)
      p2     = Producer.test(fc)
      engine = Engine.make(p2, mkAuthorStore(at), mkForecastStore(fc), DummyAcker())
      _  <- engine.run(in.asMsg)
      ae <- at.get
      fe <- fc.get
    yield ex1(ae) && ex2(fe)

  test("Successful author registration") {
    val out = AuthorEvent.Registered(eventId, cid, authorId, authorName, None, ts)
    baseTest(
      in = ForecastCommand.Register(id, cid, authorName, None, ts),
      ex1 = expect.same(_, Some(out)),
      ex2 = expect.same(_, None)
    )
  }

  test("Fail to register author (duplicate username)") {
    baseTest(
      mkAuthorStore = _ => failAuthorStore,
      in = ForecastCommand.Register(id, cid, authorName, None, ts),
      ex1 = expect.same(_, None),
      ex2 = expect.same(_, None)
    )
  }

  test("Successful forecast publishing") {
    val out = ForecastEvent.Published(eventId, cid, authorId, fid, symbol, ts)
    baseTest(
      in = ForecastCommand.Publish(id, cid, authorId, symbol, desc, tag, ts),
      ex1 = expect.same(_, None),
      ex2 = expect.same(_, Some(out))
    )
  }

  test("Fail to publish forecast (author not found)") {
    baseTest(
      mkForecastStore = _ => failForecastStore,
      in = ForecastCommand.Publish(id, cid, authorId, symbol, desc, tag, ts),
      ex1 = expect.same(_, None),
      ex2 = expect.same(_, None)
    )
  }

  test("Successful forecast voting") {
    val out = ForecastEvent.Voted(eventId, cid, fid, VoteResult.Up, ts)
    baseTest(
      in = ForecastCommand.Vote(id, cid, fid, VoteResult.Up, ts),
      ex1 = expect.same(_, None),
      ex2 = expect.same(_, Some(out))
    )
  }

  test("Fail to register author (unexpected error); nack message") {
    for
      ref <- IO.ref(none[Consumer.MsgId])
      fc  <- IO.ref(none[ForecastEvent])
      acker  = mkNAcker(ref)
      engine = Engine.make(Producer.dummy, unexpectedFailAuthorStore, okForecastStore(fc), acker)
      _    <- engine.run(ForecastCommand.Register(id, cid, authorName, None, ts).asMsg)
      nack <- ref.get
    yield expect.same(nack, Some(msgId))
  }
