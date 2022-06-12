package trading.events

import trading.commands.ForecastCommand
import trading.domain.*

import io.circe.Codec

enum AuthorEvent derives Codec.AsObject:
  def id: EventId
  def cid: CorrelationId
  def createdAt: Timestamp

  case Registered(
      id: EventId,
      cid: CorrelationId,
      authorId: AuthorId,
      authorName: AuthorName,
      createdAt: Timestamp
  )

  case NotRegistered(
      id: EventId,
      cid: CorrelationId,
      authorName: AuthorName,
      reason: Reason,
      createdAt: Timestamp
  )
