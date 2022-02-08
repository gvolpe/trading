package trading.forecasts.store

import trading.domain.*
import trading.lib.*

import cats.effect.kernel.{ Async, MonadCancelThrow, Resource }
import cats.syntax.all.*
import doobie.Transactor
import doobie.h2.*
import doobie.implicits.*

trait ForecastStore[F[_]]:
  def fetch(fid: ForecastId): F[Option[Forecast]]
  def save(aid: AuthorId, fc: Forecast): F[Either[AuthorNotFound, Unit]]
  def castVote(fid: ForecastId, res: VoteResult): F[Either[ForecastNotFound, Unit]]

object ForecastStore:
  def from[F[_]: MonadCancelThrow](
      xa: Transactor[F]
  ): ForecastStore[F] = new:
    def fetch(fid: ForecastId): F[Option[Forecast]] =
      SQL.selectForecast(fid).option.transact(xa)

    def save(aid: AuthorId, fc: Forecast): F[Either[AuthorNotFound, Unit]] =
      val saveForecast =
        SQL.insertForecast(fc).run

      val saveRelationship =
        SQL
          .updateAuthorForecast(aid, fc.id)
          .run
          .void
          .onConstraintViolation(AuthorNotFound)

      (saveForecast *> saveRelationship).transact(xa).lift

    def castVote(fid: ForecastId, res: VoteResult): F[Either[ForecastNotFound, Unit]] =
      SQL
        .updateVote(fid, res)
        .run
        .onUpdateFailure(ForecastNotFound)
        .transact(xa)
        .lift
