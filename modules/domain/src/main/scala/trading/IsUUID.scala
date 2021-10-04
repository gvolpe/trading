package trading

import java.util.UUID

import monocle.Iso

trait IsUUID[A]:
  def iso: Iso[UUID, A]

object IsUUID:
  def apply[A](using ev: IsUUID[A]): IsUUID[A] = ev

  given IsUUID[UUID] with
    def iso: Iso[UUID, UUID] =
      Iso[UUID, UUID](identity)(identity)
