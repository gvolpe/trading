package trading.forecasts.store

import java.util.UUID

import trading.domain.*

import cats.syntax.show.*
import doobie.*
import doobie.implicits.*

object SQL:
  given Meta[UUID] = Meta[String].imap[UUID](UUID.fromString)(_.toString)

  given Read[Author] = Read[(UUID, String, Option[String], Option[UUID])].map { (id, name, website, fid) =>
    Author(AuthorId(id), AuthorName(name), website.map(Website(_)), fid.toSet.map(ForecastId(_)))
  }

  given Read[Forecast] = Read[(UUID, String, String, String, Int)].map { (id, sl, tag, desc, sc) =>
    Forecast(ForecastId(id), Symbol.unsafeFrom(sl), ForecastTag.from(tag), ForecastDescription(desc), ForecastScore(sc))
  }

  /* ---------------------- authors table ----------------------- */
  val selectAuthor: AuthorId => Query0[Author] = id => sql"""
      SELECT a.id, a.name, a.website, f.id FROM authors AS a
      LEFT JOIN author_forecasts AS f ON a.id=f.author_id
      WHERE a.id=${id.show}
    """.query[Author]

  val insertAuthor: Author => Update0 = a => sql"""
      INSERT INTO authors (id, name, website)
      VALUES (${a.id.value}, ${a.name.value}, ${a.website.map(_.value)})
    """.update

  /* ---------------------- author_forecasts table ----------------------- */
  def insertAuthorForecasts(a: Author): ConnectionIO[Int] =
    val sql = "INSERT INTO author_forecasts (id, author_id) VALUES (?, ?)"
    val ids = a.forecasts.toList.map(_.value -> a.id.value)
    Update[(UUID, UUID)](sql).updateMany(ids)

  def updateAuthorForecast(id: AuthorId, fid: ForecastId): Update0 =
    sql"""
      INSERT INTO author_forecasts (id, author_id)
      VALUES (${fid.value}, ${id.value})
    """.update

  /* ---------------------- forecasts table ----------------------- */
  val selectForecast: ForecastId => Query0[Forecast] = id => sql"""
      SELECT id, symbol, tag, description, score FROM forecasts
      WHERE id=${id.show}
    """.query[Forecast]

  val insertForecast: Forecast => Update0 = f => sql"""
      INSERT INTO forecasts (id, symbol, tag, description, score)
      VALUES (${f.id.value}, ${f.symbol.show}, ${f.tag.show}, ${f.description.show}, ${f.score.value})
    """.update

  def updateVote(id: ForecastId, res: VoteResult): Update0 =
    sql"""
      UPDATE forecasts
      SET score=COALESCE(score, 0)+${res.asInt}
      WHERE id=${id.show}
    """.update
