package trading.forecasts.cdc

import trading.domain.*
import trading.events.*

import io.circe.Codec
import io.circe.Decoder

// Upgrading from circe 0.14.6 to 0.14.7 requires this instance ¯\_(ツ)_/¯ 
given Codec[Either[AuthorEvent, ForecastEvent]] = Codec.AsObject.derived

final case class OutboxEvent(
    event_id: EventId,
    correlation_id: CorrelationId,
    event: Either[AuthorEvent, ForecastEvent],
    created_at: Timestamp
) derives Codec.AsObject
