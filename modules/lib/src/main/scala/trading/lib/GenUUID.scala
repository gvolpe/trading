package trading.lib

import java.util.UUID

import cats.effect.kernel.Sync

trait GenUUID[F[_]]:
  def random: F[UUID]

object GenUUID {
  def apply[F[_]](using ev: GenUUID[F]): GenUUID[F] = ev

  given forSync[F[_]](using F: Sync[F]): GenUUID[F] =
    new GenUUID[F] {
      def random: F[UUID] = F.delay(UUID.randomUUID())
    }
}
