package trading.forecasts.store

import java.util.UUID

import trading.domain.*

import cats.data.NonEmptyList
import cats.effect.*
import doobie.h2.H2Transactor
import weaver.IOSuite

object SQLSuite extends IOSuite:
  type Res = H2Transactor[IO]

  override def sharedResource: Resource[IO, Res] =
    DB.makeTransactor

  val aid  = AuthorId(UUID.randomUUID())
  val aid2 = AuthorId(UUID.randomUUID())
  val fid  = ForecastId(UUID.randomUUID())
  val fid2 = ForecastId(UUID.randomUUID())
  val fid3 = ForecastId(UUID.randomUUID())

  test("authors") { xa =>
    val authors = AuthorStore.from(xa)

    for
      _ <- authors.save(Author(aid, AuthorName("gvolpe"), None, Set(fid)))
      _ <- authors.addForecast(aid, fid2)
      x <- authors.fetch(aid)
      y <- authors.addForecast(aid, fid2).attempt
      w <- authors.save(Author(aid2, AuthorName("gvolpe"), None, Set())).attempt
      z <- authors.addForecast(aid2, fid3).attempt
    yield NonEmptyList
      .of(
        expect.same(x.map(_.id), Some(aid)),
        expect.same(y, Left(AuthorStore.DuplicateForecastError)),
        expect.same(w, Left(AuthorStore.DuplicateAuthorError)),
        expect.same(z, Left(AuthorStore.AuthorNotFound))
      )
      .reduce
  }
