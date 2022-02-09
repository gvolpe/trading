package trading.lib

import scala.annotation.nowarn
import scala.reflect.ClassTag

import cats.MonadThrow
import cats.syntax.all.*
import fs2.{ Pull, Stream }

export Logger.redisLog

extension [F[_], A](src: Stream[F, A])
  /* Perform an action when we get the first message without consuming it twice */
  def onFirstMessage(action: F[Unit]): Stream[F, A] =
    src.pull.uncons.flatMap {
      case Some((chunk, tl)) =>
        Pull.eval(action) >> Pull.output(chunk) >> tl.pull.echo
      case None => Pull.done
    }.stream

extension [E <: Throwable, A](ut: E | A)
  @nowarn
  def asEither: Either[E, A] =
    ut match
      case e: E => Left(e)
      case a: A => Right(a)

extension [E, A](either: Either[E, A])
  def asUnionType: E | A =
    either match
      case Left(e: E)  => e
      case Right(a: A) => a

extension [F[_]: MonadThrow, A](fa: F[A])
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
    lift.map(_.asUnionType)

extension [F[_]: MonadThrow, E <: Throwable, A](fa: F[E | A])
  /* Same as `rethrow`, except it operates on `F[E | A]` instead of `F[Either[E, A]]` */
  def rethrowU: F[A] =
    fa.map(_.asEither).rethrow
