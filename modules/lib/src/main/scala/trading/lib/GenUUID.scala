package trading.lib

import java.util.UUID

import trading.IsUUID

import cats.effect.kernel.Sync
import cats.syntax.functor.*

trait GenUUID[F[_]]:
  def make[A: IsUUID]: F[A]

object GenUUID:
  def apply[F[_]: GenUUID]: GenUUID[F] = summon

  given [F[_]: Sync]: GenUUID[F] with
    def make[A: IsUUID]: F[A] =
      Sync[F].delay(UUID.randomUUID()).map(IsUUID[A].iso.get)
