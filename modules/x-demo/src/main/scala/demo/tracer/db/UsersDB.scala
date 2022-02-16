package demo.tracer
package db

import java.util.UUID

import scala.util.control.NoStackTrace

import cats.{ Monad, MonadThrow }
import cats.effect.kernel.Ref
import cats.syntax.all.*
import natchez.Trace

trait UsersDB[F[_]]:
  def get(id: UUID): F[Option[User]]
  def save(user: User): F[Either[UsersDB.DuplicateUser, Unit]]

object UsersDB:
  case object DuplicateUser extends NoStackTrace
  type DuplicateUser = DuplicateUser.type

  def make[F[_]: MonadThrow: Ref.Make: Trace]: F[UsersDB[F]] =
    (
      Ref.of[F, Map[UUID, User]](Map.empty),
      Ref.of[F, Map[String, UUID]](Map.empty)
    ).tupled.map { (users, idx) =>
      new:
        def get(id: UUID): F[Option[User]] =
          Trace[F].span("users-db") {
            Trace[F].put("fetch" -> id.toString) *>
              users.get.map(_.get(id))
          }

        def save(user: User): F[Either[DuplicateUser, Unit]] =
          Trace[F].span("users-db") {
            idx.get
              .map(_.get(user.name))
              .flatMap {
                case Some(_) =>
                  Trace[F].put("duplicate-error" -> user.name) *>
                    DuplicateUser.raiseError
                case None =>
                  Trace[F].put("new-user" -> user.name) *>
                    users.update(_.updated(user.id, user)) *>
                    idx.update(_.updated(user.name, user.id))
              }
              .attemptNarrow
          }
    }

  def alt[F[_]: Monad: Ref.Make, G[_]: MonadThrow: Trace](using NT[F, G]): F[UsersDB[G]] =
    (
      Ref.of[F, Map[UUID, User]](Map.empty),
      Ref.of[F, Map[String, UUID]](Map.empty)
    ).tupled.map { (users, idx) =>
      new:
        def get(id: UUID): G[Option[User]] =
          Trace[G].span("users-db") {
            Trace[G].put("fetch" -> id.toString) *>
              users.get.map(_.get(id)).liftK
          }

        def save(user: User): G[Either[DuplicateUser, Unit]] =
          Trace[G].span("users-db") {
            idx.get.liftK
              .map(_.get(user.name))
              .flatMap {
                case Some(_) =>
                  Trace[G].put("duplicate-error" -> user.name) *>
                    DuplicateUser.raiseError
                case None =>
                  Trace[G].put("new-user" -> user.name) *>
                    (
                      users.update(_.updated(user.id, user)) *>
                        idx.update(_.updated(user.name, user.id))
                    ).liftK
              }
              .attemptNarrow
          }
    }
