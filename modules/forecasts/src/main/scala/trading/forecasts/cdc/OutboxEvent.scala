package trading.forecasts.cdc

import trading.domain.*
import trading.events.*

import io.circe.Codec

final case class OutboxEvent(
    event_id: EventId,
    correlation_id: CorrelationId,
    event: Either[AuthorEvent, ForecastEvent],
    created_at: Timestamp
) derives Codec.AsObject
