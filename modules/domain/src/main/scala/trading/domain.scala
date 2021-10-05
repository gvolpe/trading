package trading.domain

import java.time.Instant
import java.util.UUID

import trading.{ IdNewtype, Newtype, NumNewtype }

import cats.{ Eq, Order, Show }
import io.circe.*

export OrphanInstances.given

type Symbol = Symbol.Type
object Symbol extends Newtype[String]

type Timestamp = Timestamp.Type
object Timestamp extends Newtype[Instant]

type Quantity = Quantity.Type
object Quantity extends Newtype[Int]

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
