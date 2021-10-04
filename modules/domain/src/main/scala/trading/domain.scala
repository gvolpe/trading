package trading.domain

import java.time.Instant
import java.util.UUID

import trading.{ IdNewtype, Newtype }

import cats.{ Eq, Show }
import io.circe.*

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

type Price     = BigDecimal
type AskPrice  = Price
type BidPrice  = Price
type TickPrice = Double
type TickSize  = Double

// orphan instances go below here //
given Eq[Instant]   = Eq.by(_.getEpochSecond)
given Show[Instant] = Show.show[Instant](_.toString)
