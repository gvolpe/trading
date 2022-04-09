package trading.events

import trading.commands.SwitchCommand
import trading.domain.{ given, * }

import cats.{ Applicative, Eq, Show }
// FIXME: importing all `given` yield ambiguous implicits
import cats.derived.semiauto.{ derived, product }
import cats.syntax.all.*
import io.circe.Codec
import monocle.Traversal

sealed trait SwitchEvent derives Codec.AsObject, Show:
  def id: EventId
  def cid: CorrelationId
  def createdAt: Timestamp

object SwitchEvent:
  final case class Started(
      id: EventId,
      cid: CorrelationId,
      createdAt: Timestamp
  ) extends SwitchEvent

  final case class Stopped(
      id: EventId,
      cid: CorrelationId,
      createdAt: Timestamp
  ) extends SwitchEvent

  final case class Ignored(
      id: EventId,
      cid: CorrelationId,
      createdAt: Timestamp
  ) extends SwitchEvent

  // EventId and Timestamp are regenerated when reprocessed so we don't consider them for deduplication.
  given Eq[SwitchEvent] = Eq.by(_.cid)

  val _CorrelationId: Traversal[SwitchEvent, CorrelationId] = new:
    def modifyA[F[_]: Applicative](f: CorrelationId => F[CorrelationId])(s: SwitchEvent): F[SwitchEvent] =
      f(s.cid).map { newCid =>
        s match
          case c: Ignored => c.copy(cid = newCid)
          case c: Started => c.copy(cid = newCid)
          case c: Stopped => c.copy(cid = newCid)
      }
