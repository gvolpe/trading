package trading.core

import java.time.Instant

import trading.domain.Timestamp

import cats.effect.kernel.Sync

trait Time[F[_]] {
  def timestamp: F[Timestamp]
}

object Time {
  @inline def apply[F[_]: Time]: Time[F] = implicitly

  val dummyTs: Timestamp = Instant.parse("2021-09-16T14:00:00.00Z")

  implicit def forSync[F[_]: Sync]: Time[F] =
    new Time[F] {
      def timestamp: F[Timestamp] = Sync[F].delay(Instant.now())
    }
}
