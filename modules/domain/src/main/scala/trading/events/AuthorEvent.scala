package trading.events

import trading.commands.ForecastCommand
import trading.domain.*

import io.circe.Codec

sealed trait AuthorEvent derives Codec.AsObject:
  def id: EventId
  def timestamp: Timestamp

object AuthorEvent:
  final case class Registered(
      id: EventId,
      authorId: AuthorId,
      authorName: AuthorName,
      timestamp: Timestamp
  ) extends AuthorEvent

  final case class NotRegistered(
      id: EventId,
      authorName: AuthorName,
      reason: String,
      timestamp: Timestamp
  ) extends AuthorEvent

  final case class ReputationUpdated(
      id: EventId,
      authorId: AuthorId,
      authorName: AuthorName,
      newReputation: Reputation,
      timestamp: Timestamp
  ) extends AuthorEvent
