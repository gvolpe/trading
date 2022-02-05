package trading.forecasts.store

import java.sql.SQLException

import scala.util.control.NoStackTrace

import cats.MonadThrow
import cats.syntax.all.*

case object AuthorOrForecastNotFound extends NoStackTrace
case object DuplicateForecastError   extends NoStackTrace
case object DuplicateAuthorError     extends NoStackTrace
case object ForecastNotFound         extends NoStackTrace

extension [F[_]: MonadThrow, A](fa: F[A])
  /* duplicate key violates unique constraint */
  def onDuplicate(err: Throwable): F[A] =
    fa.adaptError {
      case e: SQLException if e.getSQLState == "23505" => err
    }

  /* referential integrity constraint violation */
  def onConstraintViolation(err: Throwable): F[A] =
    fa.adaptError {
      case e: SQLException if e.getSQLState == "23506" => err
    }

extension [F[_]: MonadThrow](fa: F[Int])
  /* for update-set statements */
  def onUpdateFailure(err: Throwable): F[Unit] =
    fa.flatMap {
      case 1 => ().pure[F]
      case _ => err.raiseError[F, Unit]
    }
