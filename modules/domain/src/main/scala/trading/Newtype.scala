package trading

import java.util.UUID

import cats.syntax.all.*
import cats.{ Eq, Show }
import io.circe.{ Decoder, Encoder }
import monocle.Iso

abstract class Newtype[A](using
    eqv: Eq[A],
    shw: Show[A],
    enc: Encoder[A],
    dec: Decoder[A]
):
  opaque type Type = A

  inline def apply(a: A): Type = a

  extension (t: Type) inline def value: A = t

  given Eq[Type]      = eqv
  given Show[Type]    = shw
  given Encoder[Type] = enc
  given Decoder[Type] = dec

abstract class IdNewtype extends Newtype[UUID]:
  given IsUUID[Type] with
    def iso: Iso[UUID, Type] =
      Iso[UUID, Type](apply(_))(_.value)
