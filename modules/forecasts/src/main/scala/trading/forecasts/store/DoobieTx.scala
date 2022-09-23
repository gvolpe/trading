package trading.forecasts.store

import trading.lib.Logger

import cats.~>
import cats.arrow.FunctionK
import cats.effect.kernel.{ Async, Resource }
import cats.effect.kernel.Resource.ExitCase.*
import cats.effect.syntax.all.*
import cats.syntax.all.*
import doobie.{ ConnectionIO, Transactor, WeakAsync }
import doobie.free.connection.setAutoCommit
import doobie.hi.connection.{ commit, rollback }

/* Gives you something similar to Skunk's transactions: https://tpolecat.github.io/skunk/tutorial/Transactions.html */
trait DoobieTx[F[_]]:
  def transaction(xa: Transactor[F]): Resource[F, ConnectionIO ~> F]

object DoobieTx:
  def apply[F[_]: DoobieTx]: DoobieTx[F] = summon

  given [F[_]: Async: Logger]: DoobieTx[F] with
    def transaction(xa: Transactor[F]): Resource[F, ConnectionIO ~> F] =
      WeakAsync.liftK[F, ConnectionIO].flatMap { fk =>
        xa.connect(xa.kernel).flatMap { conn =>
          def log(s: => String) = fk(Logger[F].debug(s"DB: $s"))

          val rawTrans = FunctionK.lift[ConnectionIO, F] {
            [T] => (_: ConnectionIO[T]).foldMap(xa.interpret).run(conn)
          }

          Resource
            .makeCase(setAutoCommit(false)) {
              case (_, Succeeded)             => log("COMMIT") *> commit
              case (_, Canceled | Errored(_)) => log("ROLLBACK") *> rollback
            }
            .mapK(rawTrans)
            .as(rawTrans)
        }
      }
