package demo.tracer
package http

import java.util.UUID

import demo.tracer.db.UsersDB
import trading.lib.GenUUID

import cats.Monad
import cats.syntax.all.*
import natchez.Trace
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

final class Routes[F[_]: GenUUID: Monad: Trace](
    users: UsersDB[F]
) extends Http4sDsl[F]:

  val routes: HttpRoutes[F] = HttpRoutes.of {
    case GET -> Root / "v1" / "users" / UUIDVar(id) =>
      Trace[F].span("http") {
        Trace[F].put("get-user" -> id.toString) *>
          users.get(id).flatMap {
            case Some(u) =>
              Trace[F].put("ok" -> u.name) *> Ok(u.name)
            case None =>
              Trace[F].put("not-found" -> id.toString) *> NotFound()
          }
      }

    case POST -> Root / "v1" / "users" / name =>
      Trace[F].span("http") {
        GenUUID[F].make[UUID].flatMap { id =>
          Trace[F].put("save-user" -> name) *>
            users.save(User(id, name)).flatMap {
              case Left(UsersDB.DuplicateUser) =>
                Trace[F].put("conflict" -> name) *> Conflict()
              case Right(_) =>
                Trace[F].put("ok" -> name) *> Created(id.toString)
            }
        }
      }
  }
