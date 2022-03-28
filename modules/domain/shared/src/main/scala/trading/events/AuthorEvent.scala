package trading.events

import trading.commands.ForecastCommand
import trading.domain.*

import io.circe.Codec

sealed trait AuthorEvent derives Codec.AsObject:
  def id: EventId
  def cid: CorrelationId
  def createdAt: Timestamp

object AuthorEvent:
  final case class Registered(
      id: EventId,
      cid: CorrelationId,
      authorId: AuthorId,
      authorName: AuthorName,
      createdAt: Timestamp
  ) extends AuthorEvent

  final case class NotRegistered(
      id: EventId,
      cid: CorrelationId,
      authorName: AuthorName,
      reason: Reason,
      createdAt: Timestamp
  ) extends AuthorEvent
