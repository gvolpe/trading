package trading.events

import trading.commands.TradeCommand
import trading.domain.{ given, * }

import cats.{ Applicative, Eq, Show }
// FIXME: importing all `given` yield ambiguous implicits
import cats.derived.semiauto.{ derived, product }
import cats.syntax.all.*
import io.circe.Codec
import monocle.Traversal

sealed trait TradeEvent derives Codec.AsObject, Show:
  def id: EventId
  def cid: CorrelationId
  def command: TradeCommand
  def createdAt: Timestamp

object TradeEvent:
  final case class CommandExecuted(
      id: EventId,
      cid: CorrelationId,
      command: TradeCommand,
      createdAt: Timestamp
  ) extends TradeEvent

  final case class CommandRejected(
      id: EventId,
      cid: CorrelationId,
      command: TradeCommand,
      reason: Reason,
      createdAt: Timestamp
  ) extends TradeEvent

  // EventId and Timestamp are regenerated when reprocessed so we don't consider them for deduplication.
  given Eq[TradeEvent] = Eq.and(Eq.by(_.cid), Eq.by(_.command))

  val _CorrelationId: Traversal[TradeEvent, CorrelationId] = new:
    def modifyA[F[_]: Applicative](f: CorrelationId => F[CorrelationId])(s: TradeEvent): F[TradeEvent] =
      f(s.cid).map { newCid =>
        s match
          case c: CommandExecuted => c.copy(cid = newCid)
          case c: CommandRejected => c.copy(cid = newCid)
      }
