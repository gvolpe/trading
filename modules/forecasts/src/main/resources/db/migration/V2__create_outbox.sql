CREATE TABLE outbox (
  event_id UUID PRIMARY KEY,
  correlation_id UUID NOT NULL,
  event TEXT NOT NULL,
  created_at DATETIME
);

CREATE TRIGGER h2_cdc
AFTER INSERT
ON outbox
FOR EACH ROW
CALL "trading.forecasts.cdc.H2OutboxTrigger";
