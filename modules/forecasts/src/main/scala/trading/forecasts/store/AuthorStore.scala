package trading.forecasts.store

import trading.domain.*

import cats.effect.kernel.{ Async, MonadCancelThrow, Resource }
import cats.syntax.all.*
import doobie.Transactor
import doobie.h2.*
import doobie.implicits.*

trait AuthorStore[F[_]]:
  def fetch(id: AuthorId): F[Option[Author]]
  def save(author: Author): F[Unit]

object AuthorStore:
  def from[F[_]: MonadCancelThrow](
      xa: Transactor[F]
  ): AuthorStore[F] = new:
    def fetch(id: AuthorId): F[Option[Author]] =
      SQL.selectAuthor(id).accumulate[List].transact(xa).map {
        case Nil       => None
        case (x :: xs) => x.copy(forecasts = x.forecasts.union(xs.toSet.flatMap(_.forecasts))).some
      }

    def save(author: Author): F[Unit] =
      val saveAuthor =
        SQL
          .insertAuthor(author)
          .run
          .onDuplicate(DuplicateAuthorError)

      // this is not used in Engine but it's here to demonstrate batch inserts
      val saveForecasts =
        SQL
          .insertAuthorForecasts(author)
          .whenA(author.forecasts.nonEmpty)
          .onDuplicate(DuplicateForecastError)
          .onConstraintViolation(ForecastNotFound)

      (saveAuthor *> saveForecasts).transact(xa)
