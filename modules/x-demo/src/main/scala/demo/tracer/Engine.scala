package demo.tracer

import java.util.UUID

import demo.tracer.db.UsersDB

import trading.lib.Consumer.{ Msg, MsgId }
import trading.lib.{ GenUUID, Producer }

import cats.Monad
import cats.syntax.all.*
import natchez.Trace

object Engine:
  def one[F[_]: GenUUID: Monad: Trace](
      producer: Producer[F, User],
      users: UsersDB[F],
      ack: MsgId => F[Unit]
  ): Msg[String] => F[Unit] = { case Msg(msgId, _, name) =>
    Trace[F].span("name-consumer") {
      Trace[F].put("new-username" -> name) *>
        GenUUID[F].make[UUID].flatMap { id =>
          users.save(User(id, name)).flatMap {
            case Left(UsersDB.DuplicateUser) =>
              Trace[F].put("duplicate" -> name)
            case Right(_) =>
              Trace[F].put("ok" -> name) *> Trace[F].kernel.flatMap { kernel =>
                producer.send(User(id, name), kernel.toHeaders)
              }
          } *> ack(msgId)
        }
    }
  }

  // format: off
  def two[F[_]: GenUUID, G[_]: Monad: Trace](
      producer: Producer[F, User],
      users: UsersDB[G],
      ack: MsgId => F[Unit]
  )(using NT[F, G]): Msg[String] => G[Unit] = { case Msg(msgId, _, name) =>
    Trace[G].span("name-consumer") {
      Trace[G].put("new-username" -> name) *>
        GenUUID[F].make[UUID].liftK.flatMap { id =>
          users.save(User(id, name)).flatMap {
            case Left(UsersDB.DuplicateUser) =>
              Trace[G].put("duplicate" -> name)
            case Right(_) =>
              Trace[G].put("ok" -> name) *> Trace[G].kernel.flatMap { kernel =>
                producer.send(User(id, name), kernel.toHeaders).liftK
              }
          } *> ack(msgId).liftK
        }
    }
  }
