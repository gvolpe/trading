package trading.core

import trading.domain.Timestamp
import cats.effect.kernel.Sync
import java.time.Instant

trait Time[F[_]] {
  def timestamp: F[Timestamp]
}

object Time {
  @inline def apply[F[_]: Time]: Time[F] = implicitly

  implicit def forSync[F[_]: Sync]: Time[F] =
    new Time[F] {
      def timestamp: F[Timestamp] = Sync[F].delay(Instant.now())
    }
}
