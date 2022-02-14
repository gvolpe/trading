package trading.events

import trading.commands.TradeCommand
import trading.domain.{ given, * }

import cats.{ Applicative, Show }
import cats.derived.*
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
