package demo

import java.util.UUID

import trading.lib.Logger
import trading.forecasts.store.*
import trading.forecasts.store.{ DB, DoobieTx }

import cats.~>
import cats.effect.*
import cats.effect.kernel.Resource.ExitCase
import cats.effect.syntax.all.*
import cats.syntax.all.*
import doobie.*
import doobie.ExecutionContexts
import doobie.h2.*
import doobie.hi.connection.{ commit, rollback }
import doobie.free.connection.setAutoCommit
import doobie.implicits.*

object DoobieTxDemo extends IOApp.Simple:
  given Meta[UUID] = Meta[String].imap[UUID](UUID.fromString)(_.toString)

  val insertAuthor: AuthorDTO => Update0 = a => sql"""
      INSERT INTO authors (id, name, website)
      VALUES (${a.id.value}, ${a.name.value}, ${a.website.map(_.value)})
    """.update

  val author = PulsarCDC.authors.head

  val dummyRes = Resource.makeCase(IO.println("Acquire 1").as(1)) {
    case (x, ExitCase.Succeeded)  => IO.println(s"Release $x Succeeded")
    case (x, ExitCase.Canceled)   => IO.println(s"Release $x Canceled")
    case (x, ExitCase.Errored(e)) => IO.println(s"Release $x Errored: ${e.getMessage}")
  }

  val statement = insertAuthor(author).run.void

  val run: IO[Unit] =
    DB.init[IO].use { xa =>
      (xa.transaction, dummyRes)
        .mapN { (f, x) =>
          IO.println(s"Using $x") *>
            f(statement) // *> statement) /* uncomment 2nd statement to see rollback */
              .flatMap { _ =>
                IO.println("After insert statements")
              }
              .onError { e =>
                IO.println(s"ERROR After 2nd insert: ${e.getMessage}")
              }
            *> IO.raiseError(new Exception("boom")) /* uncomment this to see rollback */
        }
        .use(identity)
        .handleErrorWith(e => IO.println(s"After txs: ${e.getMessage}"))
    }
