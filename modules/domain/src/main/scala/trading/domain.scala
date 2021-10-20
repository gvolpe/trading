package trading.domain

import java.time.Instant
import java.util.UUID

import trading.{ IdNewtype, Newtype, NumNewtype, Wrapper }

import cats.{ Eq, Order, Show }
import ciris.ConfigValue
import io.circe.*

export OrphanInstances.given

extension [F[_], A](cv: ConfigValue[F, A])
  def fallback[Raw](value: Raw)(using ev: Wrapper[Raw, A]): ConfigValue[F, A] =
    cv.default(ev.iso.get(value))

type PulsarURI = PulsarURI.Type
object PulsarURI extends Newtype[String]

type RedisURI = RedisURI.Type
object RedisURI extends Newtype[String]

type Symbol = Symbol.Type
object Symbol extends Newtype[String]

type Timestamp = Timestamp.Type
object Timestamp extends Newtype[Instant]

type Quantity = Quantity.Type
object Quantity extends NumNewtype[Int]

type Source = String
object Source extends Newtype[String]

type CommandId = CommandId.Type
object CommandId extends IdNewtype

type SocketId = SocketId.Type
object SocketId extends IdNewtype

type Price = Price.Type
object Price extends NumNewtype[BigDecimal]

type AskPrice = Price
type BidPrice = Price
