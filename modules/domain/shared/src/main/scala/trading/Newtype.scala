package trading

import java.util.UUID

import cats.{ Eq, Order, Show }
import io.circe.{ Decoder, Encoder }
import monocle.Iso

// this can be used for simple newtypes without pre-defined typeclass derivations
abstract class Newt[A]:
  opaque type Type = A
  inline def apply(a: A): Type            = a
  extension (t: Type) inline def value: A = t

  protected inline final def derive[F[_]](using ev: F[A]): F[Type] = ev

abstract class Newtype[A](using
    eqv: Eq[A],
    ord: Order[A],
    shw: Show[A],
    enc: Encoder[A],
    dec: Decoder[A]
):
  opaque type Type = A

  inline def apply(a: A): Type = a

  protected inline final def derive[F[_]](using ev: F[A]): F[Type] = ev

  extension (t: Type) inline def value: A = t

  given Wrapper[A, Type] with
    def iso: Iso[A, Type] =
      Iso[A, Type](apply(_))(_.value)

  given Eq[Type]       = eqv
  given Order[Type]    = ord
  given Show[Type]     = shw
  given Encoder[Type]  = enc
  given Decoder[Type]  = dec
  given Ordering[Type] = ord.toOrdering

abstract class IdNewtype extends Newtype[UUID]:
  given IsUUID[Type]                = derive[IsUUID]
  def unsafeFrom(str: String): Type = apply(UUID.fromString(str))

abstract class NumNewtype[A: Decoder: Encoder: Eq: Order: Show](using
    num: Numeric[A]
) extends Newtype[A]:

  extension (x: Type)
    inline def -[T](using inv: T =:= Type)(y: T): Type = apply(num.minus(x.value, inv.apply(y).value))
    inline def +[T](using inv: T =:= Type)(y: T): Type = apply(num.plus(x.value, inv.apply(y).value))
