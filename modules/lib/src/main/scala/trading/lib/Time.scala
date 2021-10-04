package trading.lib

import java.time.Instant

import trading.domain.Timestamp

import cats.effect.kernel.Sync
import cats.syntax.functor.*

trait Time[F[_]]:
  def timestamp: F[Timestamp]

object Time:
  def apply[F[_]](using ev: Time[F]): Time[F] = ev

  given forSync[F[_]](using F: Sync[F]): Time[F] =
    new Time[F] {
      def timestamp: F[Timestamp] = F.delay(Instant.now()).map(t => Timestamp(t))
    }
