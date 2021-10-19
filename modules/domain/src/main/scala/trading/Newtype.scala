package trading

import java.util.UUID

import cats.{ Eq, Order, Show }
import ciris.{ ConfigDecoder, ConfigValue }
import io.circe.{ Decoder, Encoder }
import monocle.Iso

import trading.domain.OrphanInstances.given

abstract class Newtype[A](using
    eqv: Eq[A],
    ord: Order[A],
    shw: Show[A],
    enc: Encoder[A],
    dec: Decoder[A],
    cfg: ConfigDecoder[String, A]
):
  opaque type Type = A

  inline def apply(a: A): Type = a

  protected inline final def derive[F[_]](using ev: F[A]): F[Type] = ev

  extension (t: Type) inline def value: A = t

  given Eq[Type]                    = eqv
  given Order[Type]                 = ord
  given Show[Type]                  = shw
  given Encoder[Type]               = enc
  given Decoder[Type]               = dec
  given ConfigDecoder[String, Type] = cfg
  given Ordering[Type]              = ord.toOrdering

abstract class IdNewtype extends Newtype[UUID]:
  given IsUUID[Type] = derive[IsUUID]

abstract class NumNewtype[A](using
    eqv: Eq[A],
    ord: Order[A],
    shw: Show[A],
    enc: Encoder[A],
    dec: Decoder[A],
    cfg: ConfigDecoder[String, A],
    num: Numeric[A]
) extends Newtype[A]:

  extension (x: Type)
    inline def -[T](using inv: T =:= Type)(y: T): Type = apply(num.minus(x.value, inv.apply(y).value))
    inline def +[T](using inv: T =:= Type)(y: T): Type = apply(num.plus(x.value, inv.apply(y).value))
