package trading.domain

import java.time.Instant
import java.util.UUID

import scala.concurrent.duration.{ Duration, FiniteDuration }

import cats.*
import cats.syntax.all.*
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
