package trading.domain

import java.time.Instant
import java.util.UUID

import scala.concurrent.duration.FiniteDuration

import trading.*

import cats.{ Eq, Order, Show }
import io.circe.*

export OrphanInstances.given

type PulsarURI = PulsarURI.Type
object PulsarURI extends Newtype[String]

type RedisURI = RedisURI.Type
object RedisURI extends Newtype[String]

type KeyExpiration = KeyExpiration.Type
object KeyExpiration extends Newtype[FiniteDuration]

type Timestamp = Timestamp.Type
object Timestamp extends Newtype[Instant]

type Quantity = Quantity.Type
object Quantity extends NumNewtype[Int]

type Source = String
object Source extends Newtype[String]

type CommandId = CommandId.Type
object CommandId extends IdNewtype

type AlertId = AlertId.Type
object AlertId extends IdNewtype

type EventId = EventId.Type
object EventId extends IdNewtype

type CorrelationId = CorrelationId.Type
object CorrelationId extends IdNewtype

type SocketId = SocketId.Type
object SocketId extends IdNewtype

type AuthorId = AuthorId.Type
object AuthorId extends IdNewtype

type AuthorName = AuthorName.Type
object AuthorName extends Newtype[String]

type ForecastScore = ForecastScore.Type
object ForecastScore extends NumNewtype[Int]

type ForecastId = ForecastId.Type
object ForecastId extends IdNewtype

type ForecastDescription = ForecastDescription.Type
object ForecastDescription extends Newtype[String]

type Website = Website.Type
object Website extends Newtype[String]

type Reason = Reason.Type
object Reason extends Newtype[String]

type Price = Price.Type
object Price extends NumNewtype[BigDecimal]

type AskPrice = Price
type BidPrice = Price

type HighPrice = Price
type LowPrice  = Price
