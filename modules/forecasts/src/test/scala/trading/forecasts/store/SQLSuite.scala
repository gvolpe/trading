package trading.forecasts.store

import java.util.UUID

import trading.domain.*

import cats.data.NonEmptyList
import cats.effect.*
import doobie.h2.H2Transactor
import weaver.IOSuite

object SQLSuite extends IOSuite:
  type Res = H2Transactor[IO]

  override def sharedResource: Resource[IO, Res] = DB.init[IO]

  val aid  = AuthorId(UUID.randomUUID())
  val aid2 = AuthorId(UUID.randomUUID())
  val fid  = ForecastId(UUID.randomUUID())
  val fid2 = ForecastId(UUID.randomUUID())
  val fid3 = ForecastId(UUID.randomUUID())

  val desc = ForecastDescription("test")

  test("authors and forecasts") { xa =>
    val at = AuthorStore.from(xa)
    val fc = ForecastStore.from(xa)

    for
      // author and forecast do not exist yet: AuthorOrForecastNotFound
      a <- at.addForecast(aid, fid).attempt
      // forecast does not exist yet: ForecastNotFound
      b <- at.save(Author(aid, AuthorName("gvolpe"), None, Set(fid))).attempt
      // registering a new author without any forecast succeeds
      _ <- at.save(Author(aid, AuthorName("gvolpe"), None, Set()))
      // forecast does not exist yet: AuthorOrForecastNotFound
      c <- at.addForecast(aid, fid).attempt
      // create forecast successfully
      _ <- fc.save(Forecast(fid, Symbol.EURUSD, ForecastTag.Long, desc, ForecastScore(1)))
      // now linking it to the author succeeds
      _ <- at.addForecast(aid, fid)
      // fetching the author record
      d <- at.fetch(aid)
      // trying to register a new author using the same name fails due to the unique constraint
      e <- at.save(Author(aid2, AuthorName("gvolpe"), None, Set())).attempt
      // the forecast is already registered to `aid`: DuplicateForecastError
      f <- at.addForecast(aid2, fid).attempt
      // fetching the forecast record
      g <- fc.fetch(fid)
      // cast a vote to non-existing forecast
      h <- fc.castVote(fid2, VoteResult.Up).attempt
      // cast a vote on legit forecast
      _ <- fc.castVote(fid, VoteResult.Up)
      // fetching the forecast record once again, which should have a raised score
      i <- fc.fetch(fid)
    yield NonEmptyList
      .of(
        expect.same(a, Left(AuthorOrForecastNotFound)),
        expect.same(b, Left(ForecastNotFound)),
        expect.same(c, Left(AuthorOrForecastNotFound)),
        expect.same(d.map(_.id), Some(aid)),
        expect.same(e, Left(DuplicateAuthorError)),
        expect.same(f, Left(DuplicateForecastError)),
        expect(g.nonEmpty),
        expect.same(h, Left(ForecastNotFound)),
        expect.same(g.map(_.score.value + 1), i.map(_.score.value))
      )
      .reduce
  }
