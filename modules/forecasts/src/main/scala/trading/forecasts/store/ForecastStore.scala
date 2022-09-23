package trading.forecasts.store

import trading.domain.*
import trading.lib.*

import cats.~>
import cats.effect.kernel.{ MonadCancelThrow, Resource }
import cats.syntax.all.*
import doobie.{ ConnectionIO, Transactor }
import doobie.h2.*
import doobie.implicits.*

trait ForecastStore[F[_]]:
  def fetch(fid: ForecastId): F[Option[Forecast]]
  def tx: Resource[F, TxForecastStore[F]]

trait TxForecastStore[F[_]]:
  def save(aid: AuthorId, fc: Forecast): F[Unit]
  def castVote(fid: ForecastId, res: VoteResult): F[Unit]

object ForecastStore:
  def from[F[_]: DoobieTx: MonadCancelThrow](
      xa: Transactor[F]
  ): ForecastStore[F] = new:
    def fetch(fid: ForecastId): F[Option[Forecast]] =
      SQL.selectForecast(fid).option.transact(xa)

    def tx: Resource[F, TxForecastStore[F]] =
      xa.transaction.map(transactional)

  private def transactional[F[_]: MonadCancelThrow](
      fk: ConnectionIO ~> F
  ): TxForecastStore[F] = new:
    def save(aid: AuthorId, fc: Forecast): F[Unit] =
      val saveForecast =
        SQL.insertForecast(fc).run

      val saveRelationship =
        SQL
          .updateAuthorForecast(aid, fc.id)
          .run
          .void
          .onConstraintViolation(AuthorNotFound)

      fk(saveForecast *> saveRelationship)

    def castVote(fid: ForecastId, res: VoteResult): F[Unit] = fk {
      SQL.updateVote(fid, res).run.onUpdateFailure(ForecastNotFound)
    }
