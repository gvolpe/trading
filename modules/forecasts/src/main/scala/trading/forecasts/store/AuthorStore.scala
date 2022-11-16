package trading.forecasts.store

import trading.domain.*
import trading.events.AuthorEvent
import trading.lib.*

import cats.~>
import cats.effect.kernel.{ MonadCancelThrow, Resource }
import cats.syntax.all.*
import doobie.{ ConnectionIO, Transactor }
import doobie.h2.*
import doobie.implicits.*

trait AuthorStore[F[_]]:
  def fetch(id: AuthorId): F[Option[Author]]
  def tx: Resource[F, TxAuthorStore[F]]

trait TxAuthorStore[F[_]]:
  def save(author: Author): F[Unit]
  def outbox(evt: AuthorEvent): F[Unit]

object AuthorStore:
  def from[F[_]: DoobieTx: MonadCancelThrow](
      xa: Transactor[F]
  ): AuthorStore[F] = new:
    def fetch(id: AuthorId): F[Option[Author]] =
      SQL.selectAuthor(id).accumulate[List].transact(xa).map {
        case Nil       => None
        case (x :: xs) => x.copy(forecasts = x.forecasts.union(xs.toSet.flatMap(_.forecasts))).some
      }

    def tx: Resource[F, TxAuthorStore[F]] =
      xa.transaction.map(transactional)

  private def transactional[F[_]: MonadCancelThrow](
      fk: ConnectionIO ~> F
  ): TxAuthorStore[F] = new:
    def outbox(evt: AuthorEvent): F[Unit] = fk {
      SQL.insertOutbox(evt.asLeft).run.void.onConstraintViolation(DuplicateEventId(evt.id))
    }

    def save(author: Author): F[Unit] =
      val saveAuthor =
        SQL
          .insertAuthor(author)
          .run
          .onDuplicate(DuplicateAuthorError)

      // this is not used in Engine (always Set.empty) but it's here to demonstrate batch inserts
      val saveForecasts =
        SQL
          .insertAuthorForecasts(author)
          .whenA(author.forecasts.nonEmpty)
          .onDuplicate(DuplicateForecastError)
          .onConstraintViolation(ForecastNotFound)

      fk(saveAuthor *> saveForecasts)
