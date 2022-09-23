package trading.it

import java.util.UUID

import trading.domain.*
import trading.forecasts.store.*
import trading.it.suite.ResourceSuite

import cats.data.NonEmptyList
import cats.effect.*
import cats.syntax.all.*
import doobie.h2.H2Transactor

object SQLSuite extends ResourceSuite:
  type Res = H2Transactor[IO]

  override def sharedResource: Resource[IO, Res] = DB.init[IO]

  val aid  = AuthorId(UUID.randomUUID())
  val aid2 = AuthorId(UUID.randomUUID())
  val aid3 = AuthorId(UUID.randomUUID())
  val fid  = ForecastId(UUID.randomUUID())
  val fid2 = ForecastId(UUID.randomUUID())
  val fid3 = ForecastId(UUID.randomUUID())

  val desc = ForecastDescription("test")

  test("authors and forecasts") { xa =>
    val at = AuthorStore.from(xa)
    val fc = ForecastStore.from(xa)

    for
      // forecast does not exist yet: ForecastNotFound
      a <- at.tx.use(_.save(Author(aid, AuthorName("gvolpe"), None, Set(fid)))).attempt
      // registering a new author without any forecast succeeds
      _ <- at.tx.use(_.save(Author(aid, AuthorName("gvolpe"), None, Set())))
      // create forecast with non-existing Author ID
      b <- fc.tx.use(_.save(aid2, Forecast(fid, Symbol.EURUSD, ForecastTag.Long, desc, ForecastScore(1)))).attempt
      // create forecast successfully with existing Author ID
      _ <- fc.tx.use(_.save(aid, Forecast(fid, Symbol.EURUSD, ForecastTag.Long, desc, ForecastScore(1))))
      // fetching the author record
      c <- at.fetch(aid)
      // trying to register a new author using the same name fails due to the unique constraint
      d <- at.tx.use(_.save(Author(aid2, AuthorName("gvolpe"), None, Set()))).attempt
      // fetching the forecast record
      e <- fc.fetch(fid)
      // cast a vote to non-existing forecast
      f <- fc.tx.use(_.castVote(fid2, VoteResult.Up)).attempt
      // cast a vote on legit forecast
      _ <- fc.tx.use(_.castVote(fid, VoteResult.Up))
      // fetching the forecast record once again, which should have a raised score
      g <- fc.fetch(fid)
      // cast a down-vote three times
      _ <- fc.tx.use(_.castVote(fid, VoteResult.Down).replicateA(3).void)
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
