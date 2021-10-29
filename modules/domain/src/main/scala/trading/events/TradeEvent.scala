package trading.events

import trading.commands.TradeCommand
import trading.domain.*

import cats.syntax.all.*
import io.circe.Codec
import monocle.Getter

sealed trait TradeEvent derives Codec.AsObject:
  def id: EventId
  def createdAt: Timestamp

object TradeEvent:
  final case class CommandExecuted(
      id: EventId,
      command: TradeCommand,
      createdAt: Timestamp
  ) extends TradeEvent

  final case class CommandRejected(
      id: EventId,
      command: TradeCommand,
      reason: Reason,
      createdAt: Timestamp
  ) extends TradeEvent

  final case class Started(
      id: EventId,
      createdAt: Timestamp
  ) extends TradeEvent

  final case class Stopped(
      id: EventId,
      createdAt: Timestamp
  ) extends TradeEvent

  val _Command =
    Getter[TradeEvent, Option[TradeCommand]] {
      case e: CommandExecuted => e.command.some
      case e: CommandRejected => e.command.some
      case _                  => none
    }
