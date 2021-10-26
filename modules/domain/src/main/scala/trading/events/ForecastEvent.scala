package trading.events

import trading.commands.ForecastCommand
import trading.domain.*

import io.circe.Codec

sealed trait ForecastEvent derives Codec.AsObject:
  def id: EventId
  def forecastId: ForecastId
  def timestamp: Timestamp

object ForecastEvent:
  final case class Published(
      id: EventId,
      forecastId: ForecastId,
      symbol: Symbol,
      timestamp: Timestamp
  ) extends ForecastEvent

  final case class Voted(
      id: EventId,
      forecastId: ForecastId,
      result: VoteResult,
      timestamp: Timestamp
  ) extends ForecastEvent
