package trading.forecasts.store

import cats.effect.*
import cats.syntax.all.*
import doobie.ExecutionContexts
import doobie.h2.H2Transactor
import org.flywaydb.core.Flyway

object DB:
  private val uri = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"

  val makeTransactor: Resource[IO, H2Transactor[IO]] =
    ExecutionContexts
      .fixedThreadPool[IO](32)
      .flatMap { ce =>
        H2Transactor.newH2Transactor[IO](uri, "sa", "", ce)
      }
      .evalTap {
        _.configure { ds =>
          IO(Flyway.configure().dataSource(ds).load().migrate())
        }
      }
