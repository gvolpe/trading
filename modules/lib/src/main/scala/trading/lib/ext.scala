package trading.lib

import scala.reflect.ClassTag

import trading.lib.Consumer.{ Msg, MsgId }

import cats.{ Monad, MonadThrow }
import cats.effect.kernel.Deferred
import cats.syntax.all.*
import fs2.{ Pull, Stream }
import monocle.Iso

export Logger.redisLog

extension [F[_]: Monad, A](c: Consumer[F, A])
  def rewind(id: Consumer.MsgId, gate: Deferred[F, Unit]): Stream[F, Msg[A]] =
    Stream.eval(c.lastMsgId).flatMap { lastId =>
      c.receiveM(id).evalTap { msg =>
        gate.complete(()).whenA(lastId == Some(msg.id))
      }
    }

extension [F[_], A](src: Stream[F, A])
  /* Perform an action when we get the first message without consuming it twice */
  def onFirstMessage(action: F[Unit]): Stream[F, A] =
    src.pull.uncons.flatMap {
      case Some((chunk, tl)) =>
        Pull.eval(action) >> Pull.output(chunk) >> tl.pull.echo
      case None => Pull.done
    }.stream

extension [F[_], A, B](src: Stream[F, Either[Msg[A], Msg[B]]])
  def union: Stream[F, Msg[A | B]] =
    src.map(_.fold(identity, identity).asInstanceOf[Msg[A | B]])

extension [F[_], A, B, C](src: Stream[F, Either[Either[Msg[A], Msg[B]], Msg[C]]])
  def union2: Stream[F, Msg[A | B | C]] =
    src.map {
      case Left(Left(ma))  => ma.asInstanceOf[Msg[A | B | C]]
      case Left(Right(mb)) => mb.asInstanceOf[Msg[A | B | C]]
      case Right(mc)       => mc.asInstanceOf[Msg[A | B | C]]
    }

extension [F[_]: MonadThrow, A <: Matchable](fa: F[A])
  /** Lift an F[A] into an F[Either[E, A]] where E can be an union type.
    *
    * Guarantees that:
    *
    * {{{
    * val fa: F[A] = ???
    * fa <-> fa.lift[E].rethrow
    * }}}
    *
    * Example:
    *
    * {{{
    * case class Err1() extends NoStackTrace
    * case class Err2() extends NoStackTrace
    *
    * val f: IO[Unit] = IO.raiseError(Err1())
    * val g: IO[Either[Err1, Unit]] = f.lift
    * val h: IO[Either[Err1 | Err2, Unit]] = f.lift
    * val i: IO[Unit] = h.rethrow
    * }}}
    */
  def lift[E <: Throwable: ClassTag]: F[Either[E, A]] =
    fa.attemptNarrow

  /** Same as `lift`, excepts the resulting type uses `E | A` instead of `Either[E, A]`.
    *
    * Guarantees that:
    *
    * {{{
    * val fa: F[A] = ???
    * fa <-> fa.liftU[E].rethrow
    * }}}
    *
    * Example:
    *
    * {{{
    * case class Err1() extends NoStackTrace
    * case class Err2() extends NoStackTrace
    *
    * val f: IO[Unit] = IO.raiseError(Err1())
    * val g: IO[Err1 | Unit] = f.liftU
    * val h: IO[Err1 | Err2 | Unit] = f.liftU
    * val i: IO[Unit] = h.rethrow
    * }}}
    */
  def liftU[E <: Throwable: ClassTag]: F[E | A] =
    lift.map(eitherUnionIso[E, A].get)

extension [F[_]: MonadThrow, E <: Throwable, A <: Matchable](fa: F[E | A])
  /* Same as `rethrow`, except it operates on `F[E | A]` instead of `F[Either[E, A]]` */
  def rethrowU: F[A] =
    fa.map(eitherUnionIso[E, A].reverseGet).rethrow
