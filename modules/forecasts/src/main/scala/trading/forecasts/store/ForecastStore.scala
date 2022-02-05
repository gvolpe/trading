package trading.forecasts.store

import trading.domain.*

import cats.effect.kernel.{ Async, MonadCancelThrow, Resource }
import cats.syntax.all.*
import doobie.h2.*
import doobie.implicits.*

trait ForecastStore[F[_]]:
  def fetch(fid: ForecastId): F[Option[Forecast]]
  def save(forecast: Forecast): F[Unit]
  def castVote(fid: ForecastId, res: VoteResult): F[Unit]

object ForecastStore:
  def from[F[_]: MonadCancelThrow](
      xa: H2Transactor[F]
  ): ForecastStore[F] = new:
    def fetch(fid: ForecastId): F[Option[Forecast]] =
      SQL.selectForecast(fid).option.transact(xa)

    def save(fc: Forecast): F[Unit] =
      SQL
        .insertForecast(fc)
        .run
        .void
        .onDuplicate(DuplicateForecastError)
        .transact(xa)

    def castVote(fid: ForecastId, res: VoteResult): F[Unit] =
      SQL
        .updateVote(fid, res)
        .run
        .onUpdateFailure(ForecastNotFound)
        .transact(xa)
