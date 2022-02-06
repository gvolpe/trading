package trading.events

import trading.commands.ForecastCommand
import trading.domain.*

import io.circe.Codec

sealed trait ForecastEvent derives Codec.AsObject:
  def id: EventId
  def cid: CorrelationId
  def forecastId: ForecastId
  def createdAt: Timestamp

object ForecastEvent:
  final case class Published(
      id: EventId,
      cid: CorrelationId,
      authorId: AuthorId,
      forecastId: ForecastId,
      symbol: Symbol,
      createdAt: Timestamp
  ) extends ForecastEvent

  final case class NotPublished(
      id: EventId,
      cid: CorrelationId,
      authorId: AuthorId,
      forecastId: ForecastId,
      reason: Reason,
      createdAt: Timestamp
  ) extends ForecastEvent

  final case class Voted(
      id: EventId,
      cid: CorrelationId,
      forecastId: ForecastId,
      result: VoteResult,
      createdAt: Timestamp
  ) extends ForecastEvent

  final case class NotVoted(
      id: EventId,
      cid: CorrelationId,
      forecastId: ForecastId,
      reason: Reason,
      createdAt: Timestamp
  ) extends ForecastEvent
