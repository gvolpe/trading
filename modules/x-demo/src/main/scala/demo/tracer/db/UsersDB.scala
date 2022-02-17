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
    noTrace[F].map { db =>
      new:
        def get(id: UUID): F[Option[User]] =
          Trace[F].span("users-db") {
            Trace[F].put("fetch" -> id.toString) *> db.get(id)
          }
        def save(user: User): F[Either[DuplicateUser, Unit]] =
          Trace[F].span("users-db") {
            db.save(user).flatTap {
              case Left(e) =>
                Trace[F].put("duplicate-error" -> user.name)
              case Right(_) =>
                Trace[F].put("new-user" -> user.name)
            }
          }
    }

  def alt[F[_]: MonadThrow: Ref.Make, G[_]: MonadThrow: Trace](using NT[F, G]): F[UsersDB[G]] =
    noTrace[F].map { db =>
      new:
        def get(id: UUID): G[Option[User]] =
          Trace[G].span("users-db") {
            Trace[G].put("fetch" -> id.toString) *> db.get(id).liftK
          }
        def save(user: User): G[Either[DuplicateUser, Unit]] =
          Trace[G].span("users-db") {
            db.save(user).liftK.flatTap {
              case Left(e) =>
                Trace[G].put("duplicate-error" -> user.name)
              case Right(_) =>
                Trace[G].put("new-user" -> user.name)
            }
          }
    }

  def noTrace[F[_]: MonadThrow: Ref.Make]: F[UsersDB[F]] =
    (
      Ref.of[F, Map[UUID, User]](Map.empty),
      Ref.of[F, Map[String, UUID]](Map.empty)
    ).tupled.map { (users, idx) =>
      new:
        def get(id: UUID): F[Option[User]] =
          users.get.map(_.get(id))

        def save(user: User): F[Either[DuplicateUser, Unit]] =
          idx.get
            .map(_.get(user.name))
            .flatMap {
              case Some(_) =>
                DuplicateUser.raiseError
              case None =>
                users.update(_.updated(user.id, user)) *>
                  idx.update(_.updated(user.name, user.id))
            }
            .attemptNarrow
    }
