package trading.domain

import scala.concurrent.duration.FiniteDuration

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

type KeyExpiration = KeyExpiration.Type
object KeyExpiration extends Newtype[FiniteDuration]

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

type EventId = EventId.Type
object EventId extends IdNewtype

type SocketId = SocketId.Type
object SocketId extends IdNewtype

type AuthorId = AuthorId.Type
object AuthorId extends IdNewtype

type AuthorName = AuthorName.Type
object AuthorName extends Newtype[String]

// TODO: should be a positive double from 0-100 (refinement)
type Reputation = Reputation.Type
object Reputation extends NumNewtype[Double]:
  def empty: Reputation = Reputation(0.0)

type ForecastScore = ForecastScore.Type
object ForecastScore extends NumNewtype[Int]

type ForecastId = ForecastId.Type
object ForecastId extends IdNewtype

type Website = Website.Type
object Website extends Newtype[String]

type Price = Price.Type
object Price extends NumNewtype[BigDecimal]

type AskPrice = Price
type BidPrice = Price
