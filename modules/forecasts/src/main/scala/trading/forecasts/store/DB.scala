package trading.forecasts.store

import cats.effect.kernel.{ Async, Resource }
import cats.syntax.all.*
import doobie.ExecutionContexts
import doobie.h2.H2Transactor
import org.flywaydb.core.Flyway

object DB:
  private val uri = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"

  def init[F[_]: Async]: Resource[F, H2Transactor[F]] =
    ExecutionContexts
      .fixedThreadPool[F](32)
      .flatMap { ce =>
        H2Transactor.newH2Transactor[F](uri, "sa", "", ce)
      }
      .evalTap {
        _.configure { ds =>
          Async[F].delay(Flyway.configure().dataSource(ds).load().migrate())
        }
      }
