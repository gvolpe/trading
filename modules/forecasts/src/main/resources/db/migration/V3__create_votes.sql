CREATE TABLE votes (
  event_id UUID PRIMARY KEY,
  fid UUID NOT NULL,
  result INT,
  created_at DATETIME,
  CONSTRAINT votes_forecast_key FOREIGN KEY (fid) REFERENCES forecasts(id)
);
