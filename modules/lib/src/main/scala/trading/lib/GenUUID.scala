package trading.lib

import java.util.UUID

import trading.IsUUID

import cats.effect.kernel.Sync
import cats.syntax.functor.*

trait GenUUID[F[_]]:
  def make[A: IsUUID]: F[A]

object GenUUID {
  def apply[F[_]](using ev: GenUUID[F]): GenUUID[F] = ev

  given forSync[F[_]](using F: Sync[F]): GenUUID[F] =
    new GenUUID[F] {
      def make[A: IsUUID]: F[A] =
        F.delay(UUID.randomUUID()).map(IsUUID[A].iso.get)
    }
}
