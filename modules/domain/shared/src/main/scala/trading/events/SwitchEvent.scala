package trading.events

import trading.commands.SwitchCommand
import trading.domain.{ given, * }

import cats.{ Applicative, Show }
// FIXME: importing all `given` yield ambiguous implicits
import cats.derived.semiauto.{ derived, product }
import cats.syntax.all.*
import io.circe.Codec
import monocle.Traversal

enum SwitchEvent derives Codec.AsObject, Show:
  def id: EventId
  def cid: CorrelationId
  def createdAt: Timestamp

  case Started(
      id: EventId,
      cid: CorrelationId,
      createdAt: Timestamp
  )

  case Stopped(
      id: EventId,
      cid: CorrelationId,
      createdAt: Timestamp
  )

  case Ignored(
      id: EventId,
      cid: CorrelationId,
      createdAt: Timestamp
  )

object SwitchEvent:
  val _CorrelationId: Traversal[SwitchEvent, CorrelationId] = new:
    def modifyA[F[_]: Applicative](f: CorrelationId => F[CorrelationId])(s: SwitchEvent): F[SwitchEvent] =
      f(s.cid).map { newCid =>
        s match
          case c: Ignored => c.copy(cid = newCid)
          case c: Started => c.copy(cid = newCid)
          case c: Stopped => c.copy(cid = newCid)
      }
