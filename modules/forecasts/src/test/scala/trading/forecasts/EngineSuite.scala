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
import trading.lib.Logger.NoOp.given
import trading.state.*

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.all.*
import weaver.{ Expectations, SimpleIOSuite }
import weaver.scalacheck.Checkers

object EngineSuite extends SimpleIOSuite with Checkers:
  extension (cmd: ForecastCommand) def asMsg: Consumer.Msg[ForecastCommand] = Consumer.Msg(msgId, cmd)

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
    def fetch(fid: ForecastId): IO[Option[Forecast]]         = IO.none
    def save(aid: AuthorId, fc: Forecast): IO[Unit]          = IO.unit
    def castVote(fid: ForecastId, res: VoteResult): IO[Unit] = IO.unit

  class DummyAuthorStore extends AuthorStore[IO]:
    def fetch(id: AuthorId): IO[Option[Author]] = IO.none
    def save(author: Author): IO[Unit]          = IO.unit

  val okAuthorStore: AuthorStore[IO]     = new DummyAuthorStore
  val okForecastStore: ForecastStore[IO] = new DummyForecastStore

  val failAuthorStore: AuthorStore[IO] = new DummyAuthorStore:
    override def save(author: Author): IO[Unit] = IO.raiseError(DuplicateAuthorError)

  val unexpectedFailAuthorStore: AuthorStore[IO] = new DummyAuthorStore:
    override def save(author: Author): IO[Unit] = IO.raiseError(new Exception("boom"))

  val failForecastStore: ForecastStore[IO] = new DummyForecastStore:
    override def save(aid: AuthorId, fc: Forecast): IO[Unit] = IO.raiseError(AuthorNotFound)

  val voteFailForecastStore: ForecastStore[IO] = new DummyForecastStore:
    override def castVote(fid: ForecastId, res: VoteResult): IO[Unit] = IO.raiseError(ForecastNotFound)

  val dummyAcker: Acker[IO, ForecastCommand] = new:
    def ack(id: Consumer.MsgId): IO[Unit]  = IO.unit
    def nack(id: Consumer.MsgId): IO[Unit] = IO.unit

  def mkNAcker(ref: Ref[IO, Option[Consumer.MsgId]]): Acker[IO, ForecastCommand] = new:
    def ack(id: Consumer.MsgId): IO[Unit]  = IO.unit
    def nack(id: Consumer.MsgId): IO[Unit] = ref.set(id.some)

  val msgId: Consumer.MsgId = UUID.randomUUID().toString

  private def baseTest(
      authorStore: AuthorStore[IO] = okAuthorStore,
      forecastStore: ForecastStore[IO] = okForecastStore,
      in: ForecastCommand,
      ex1: Option[AuthorEvent] => Expectations,
      ex2: Option[ForecastEvent] => Expectations
  ): IO[Expectations] =
    for
      at <- IO.ref(none[AuthorEvent])
      fc <- IO.ref(none[ForecastEvent])
      p1     = Producer.test(at)
      p2     = Producer.test(fc)
      engine = Engine.make(p1, p2, authorStore, forecastStore, dummyAcker)
      _  <- engine.run(in.asMsg)
      ae <- at.get
      fe <- fc.get
    yield ex1(ae) && ex2(fe)

  test("Successful author registration") {
    val out = AuthorEvent.Registered(eventId, cid, authorId, authorName, ts)
    baseTest(
      in = ForecastCommand.Register(id, cid, authorName, None, ts),
      ex1 = expect.same(_, Some(out)),
      ex2 = expect.same(_, None)
    )
  }

  test("Fail to register author (duplicate username)") {
    val out = AuthorEvent.NotRegistered(eventId, cid, authorName, Reason("Duplicate username"), ts)
    baseTest(
      authorStore = failAuthorStore,
      in = ForecastCommand.Register(id, cid, authorName, None, ts),
      ex1 = expect.same(_, Some(out)),
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
    val out = ForecastEvent.NotPublished(eventId, cid, authorId, fid, Reason("Author not found"), ts)
    baseTest(
      forecastStore = failForecastStore,
      in = ForecastCommand.Publish(id, cid, authorId, symbol, desc, tag, ts),
      ex1 = expect.same(_, None),
      ex2 = expect.same(_, Some(out))
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

  test("Fail to vote (forecast not found)") {
    val out = ForecastEvent.NotVoted(eventId, cid, fid, Reason("Forecast not found"), ts)
    baseTest(
      forecastStore = voteFailForecastStore,
      in = ForecastCommand.Vote(id, cid, fid, VoteResult.Up, ts),
      ex1 = expect.same(_, None),
      ex2 = expect.same(_, Some(out))
    )
  }

  test("Fail to register author (unexpected error); nack message") {
    for
      ref <- IO.ref(none[Consumer.MsgId])
      acker  = mkNAcker(ref)
      engine = Engine.make(Producer.dummy, Producer.dummy, unexpectedFailAuthorStore, okForecastStore, acker)
      _    <- engine.run(ForecastCommand.Register(id, cid, authorName, None, ts).asMsg)
      nack <- ref.get
    yield expect.same(nack, Some(msgId))
  }
