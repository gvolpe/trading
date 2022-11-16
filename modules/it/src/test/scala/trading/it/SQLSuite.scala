package trading.it

import java.time.Instant
import java.util.UUID

import trading.domain.*
import trading.events.*
import trading.forecasts.store.*
import trading.lib.Logger.NoOp.given
import trading.it.suite.ResourceSuite

import cats.data.NonEmptyList
import cats.effect.*
import cats.syntax.all.*
import doobie.h2.H2Transactor

object SQLSuite extends ResourceSuite:
  type Res = H2Transactor[IO]

  override def sharedResource: Resource[IO, Res] = DB.init[IO]

  def genId = UUID.randomUUID()

  val aid  = AuthorId(genId)
  val aid2 = AuthorId(genId)
  val aid3 = AuthorId(genId)
  val fid  = ForecastId(genId)
  val fid2 = ForecastId(genId)
  val fid3 = ForecastId(genId)

  val eid  = EventId(genId)
  val eid2 = EventId(genId)
  val cid  = CorrelationId(genId)
  val ts   = Timestamp(Instant.parse("2021-09-16T14:00:00.00Z"))

  val authorName = AuthorName("gvolpe")
  val desc       = ForecastDescription("test")
  val symbol     = Symbol.EURUSD
  val tag        = ForecastTag.Long
  val score      = ForecastScore(1)

  val registered = AuthorEvent.Registered(eid, cid, aid, authorName, None, ts)
  val published  = ForecastEvent.Published(eid2, cid, aid, fid, symbol, ts)

  def voted: ForecastEvent.Voted = ForecastEvent.Voted(EventId(genId), cid, fid, VoteResult.Up, ts)

  test("authors and forecasts") { xa =>
    val at = AuthorStore.from(xa)
    val fc = ForecastStore.from(xa)

    for
      // forecast does not exist yet: ForecastNotFound
      a <- at.tx.use(db => db.save(Author(aid, authorName, None, Set(fid))) *> db.outbox(registered)).attempt
      // registering a new author without any forecast succeeds
      _ <- at.tx.use(db => db.save(Author(aid, authorName, None, Set())) *> db.outbox(registered))
      // create forecast with non-existing Author ID
      b <- fc.tx.use(db => db.save(aid2, Forecast(fid, symbol, tag, desc, score)) *> db.outbox(published)).attempt
      // create forecast successfully with existing Author ID
      _ <- fc.tx.use(db => db.save(aid, Forecast(fid, symbol, tag, desc, score)) *> db.outbox(published))
      // fetching the author record
      c <- at.fetch(aid)
      // trying to register a new author using the same name fails due to the unique constraint
      d <- at.tx.use(_.save(Author(aid2, authorName, None, Set()))).attempt
      // fetching the forecast record
      e <- fc.fetch(fid)
      // cast a vote to non-existing forecast
      f <- fc.tx.use(_.castVote(fid2, VoteResult.Up)).attempt
      // cast a vote on legit forecast
      _ <- fc.tx.use(db => db.castVote(fid, VoteResult.Up) *> db.registerVote(voted))
      // fetching the forecast record once again, which should have a raised score
      g <- fc.fetch(fid)
      // cast a down-vote three times
      _ <- fc.tx.use(db => db.castVote(fid, VoteResult.Down).replicateA(3) *> db.registerVote(voted))
      // fetching the forecast record once again, which should have a negative score
      h <- fc.fetch(fid)
    yield NonEmptyList
      .of(
        expect.same(a, Left(ForecastNotFound)),
        expect.same(b, Left(AuthorNotFound)),
        expect.same(c.map(_.id), Some(aid)),
        expect.same(d, Left(DuplicateAuthorError)),
        expect(e.nonEmpty),
        expect.same(f, Left(ForecastNotFound)),
        expect.same(e.map(_.score.value + 1), g.map(_.score.value)),
        expect.same(e.map(_.score.value - 2), h.map(_.score.value))
      )
      .reduce
  }
