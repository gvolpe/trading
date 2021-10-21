package trading.domain

import scala.concurrent.duration.{ Duration, FiniteDuration }

import java.time.Instant
import java.util.UUID

import cats.*
import cats.syntax.all.*
import ciris.ConfigDecoder
import com.comcast.ip4s.*
import io.circe.{ Decoder, Encoder }

object OrphanInstances:
  given Eq[Instant]    = Eq.by(_.getEpochSecond)
  given Order[Instant] = Order.by(_.getEpochSecond)
  given Show[Instant]  = Show.show[Instant](_.toString)

  given Decoder[FiniteDuration] =
    Decoder[String].emap { s =>
      Duration(s) match
        case fd: FiniteDuration => fd.asRight
        case e                  => e.toString.asLeft
    }

  given Encoder[FiniteDuration] =
    Encoder[String].contramap(_.toString)

  given ConfigDecoder[String, Instant] =
    ConfigDecoder[String].mapOption("java.time.Instant")(s => Either.catchNonFatal(Instant.parse(s)).toOption)

  given ConfigDecoder[String, UUID] =
    ConfigDecoder[String].mapOption("java.util.UUID")(s => Either.catchNonFatal(UUID.fromString(s)).toOption)

  given ConfigDecoder[String, Host] =
    ConfigDecoder[String].mapOption("com.comcast.ip4s.Host")(Host.fromString)

  given ConfigDecoder[String, Port] =
    ConfigDecoder[String].mapOption("com.comcast.ip4s.Port")(Port.fromString)
