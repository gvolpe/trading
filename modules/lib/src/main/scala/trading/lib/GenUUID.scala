package trading.lib

import java.util.UUID

import cats.effect.kernel.Sync

trait GenUUID[F[_]] {
  def random: F[UUID]
}

object GenUUID {
  @inline def apply[F[_]: GenUUID]: GenUUID[F] = implicitly

  implicit def forSync[F[_]: Sync]: GenUUID[F] =
    new GenUUID[F] {
      def random: F[UUID] = Sync[F].delay(UUID.randomUUID())
    }
}
