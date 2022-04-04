package trading.events

import trading.commands.TradeCommand
import trading.domain.{ given, * }

import cats.{ Applicative, Eq, Show }
// FIXME: importing all `given` yield ambiguous implicits
import cats.derived.semiauto.{ derived, product }
import cats.syntax.all.*
import io.circe.Codec
import monocle.{ Getter, Traversal }

sealed trait TradeEvent derives Codec.AsObject, Show:
  def id: EventId
  def cid: CorrelationId
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

  final case class Started(
      id: EventId,
      cid: CorrelationId,
      createdAt: Timestamp
  ) extends TradeEvent

  final case class Stopped(
      id: EventId,
      cid: CorrelationId,
      createdAt: Timestamp
  ) extends TradeEvent

  final case class Switch(evt: Either[Started, Stopped]) derives Codec.AsObject, Show:
    def getEvent: TradeEvent = evt.fold[TradeEvent](identity, identity)

  object Switch:
    def from(evt: TradeEvent): Option[Switch] = evt match
      case e: Started => Switch(e.asLeft).some
      case e: Stopped => Switch(e.asRight).some
      case _          => none

    given Eq[Switch] = new:
      def eqv(x: Switch, y: Switch): Boolean = x.getEvent === y.getEvent

  // EventId and Timestamp are regenerated when reprocessed so we don't consider them for deduplication.
  given Eq[TradeEvent] = Eq.and(Eq.by(_.cid), Eq.by(_Command.get))

  val _Command =
    Getter[TradeEvent, Option[TradeCommand]] {
      case e: CommandExecuted => e.command.some
      case e: CommandRejected => e.command.some
      case _                  => none
    }

  val _CorrelationId: Traversal[TradeEvent, CorrelationId] = new:
    def modifyA[F[_]: Applicative](f: CorrelationId => F[CorrelationId])(s: TradeEvent): F[TradeEvent] =
      f(s.cid).map { newCid =>
        s match
          case c: CommandExecuted => c.copy(cid = newCid)
          case c: CommandRejected => c.copy(cid = newCid)
          case c: Started         => c.copy(cid = newCid)
          case c: Stopped         => c.copy(cid = newCid)
      }
