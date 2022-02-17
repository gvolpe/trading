package demo.tracer

import cats.~>
import cats.data.Kleisli
import cats.effect.IO
import natchez.Span

/* Natural transformation as typeclass */
trait NT[F[_], G[_]]:
  def fk: F ~> G

object NT:
  def apply[F[_], G[_]](using nt: NT[F, G]): NT[F, G] = nt

  given NT[IO, Kleisli[IO, Span[IO], *]] = new:
    val fk = Kleisli.liftK

  // format: off
  object syntax:
    extension [F[_], G[_], A](using nt: NT[F, G])(fa: F[A])
      def liftK: G[A] = nt.fk(fa)
