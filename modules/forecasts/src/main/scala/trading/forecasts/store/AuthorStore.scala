package trading.forecasts.store

import java.sql.SQLException
import java.util.UUID

import scala.concurrent.duration.*
import scala.util.control.NoStackTrace

import trading.domain.*
import trading.forecasts.Config

import cats.MonadThrow
import cats.effect.kernel.{ Async, MonadCancelThrow, Resource }
import cats.syntax.all.*
import doobie.*
import doobie.h2.*
import doobie.implicits.*

trait AuthorStore[F[_]]:
  def fetch(id: AuthorId): F[Option[Author]]
  def save(author: Author): F[Unit]
  def addForecast(id: AuthorId, fid: ForecastId): F[Unit]

object AuthorStore:
  case object AuthorNotFound         extends NoStackTrace
  case object DuplicateForecastError extends NoStackTrace
  case object DuplicateAuthorError   extends NoStackTrace

  def from[F[_]: MonadCancelThrow](
      xa: H2Transactor[F]
  ): AuthorStore[F] = new:
    def fetch(id: AuthorId): F[Option[Author]] =
      SQL.selectAuthor(id).accumulate[List].transact(xa).map {
        case Nil       => None
        case (x :: xs) => x.copy(forecasts = x.forecasts.union(xs.toSet.flatMap(_.forecasts))).some
      }

    def save(author: Author): F[Unit] =
      val saveAuthor = SQL.insertAuthor(author).run.transact(xa).void.adaptError {
        case e: SQLException if e.getSQLState == "23505" => DuplicateAuthorError
      }

      val saveForecasts = SQL
        .insertForecasts(author)
        .transact(xa)
        .whenA(author.forecasts.nonEmpty)
        .handleError {
          case e: SQLException if e.getSQLState === "23505" => ()
        }

      saveAuthor *> saveForecasts

    def addForecast(id: AuthorId, fid: ForecastId): F[Unit] =
      SQL.updateForecast(id, fid).run.transact(xa).void.adaptError {
        case e: SQLException if e.getSQLState === "23506" => AuthorNotFound
        case e: SQLException if e.getSQLState === "23505" => DuplicateForecastError
      }

object SQL:
  given Meta[UUID] = Meta[String].imap[UUID](UUID.fromString)(_.toString)

  given Read[Author] = Read[(UUID, String, Option[String], Option[UUID])].map { (id, name, website, fid) =>
    Author(AuthorId(id), AuthorName(name), website.map(Website(_)), fid.toSet.map(ForecastId(_)))
  }

  given Write[Author] = Write[(UUID, String, Option[String])].contramap { a =>
    (a.id.value, a.name.value, a.website.map(_.value))
  }

  val selectAuthor: AuthorId => Query0[Author] = id => sql"""
      SELECT a.id, a.name, a.website, f.id FROM authors AS a
      LEFT JOIN forecasts AS f ON a.id=f.author_id
      WHERE a.id=${id.show}
    """.query[Author]

  val insertAuthor: Author => Update0 = a => sql"""
      INSERT INTO authors (id, name, website)
      VALUES (${a.id.value}, ${a.name.value}, ${a.website.map(_.value)})
    """.update

  def insertForecasts(a: Author) =
    val sql = "INSERT INTO forecasts (id, author_id) VALUES (?, ?)"
    val ids = a.forecasts.toList.map(_.value -> a.id.value)
    Update[(UUID, UUID)](sql).updateMany(ids)

  def updateForecast(id: AuthorId, fid: ForecastId): Update0 =
    sql"""
      INSERT INTO forecasts (id, author_id)
      VALUES (${fid.value}, ${id.value})
    """.update
