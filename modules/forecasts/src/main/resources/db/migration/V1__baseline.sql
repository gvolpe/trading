CREATE TABLE authors (
  id UUID PRIMARY KEY,
  name VARCHAR UNIQUE NOT NULL,
  website TEXT NULL
);

CREATE TABLE forecasts (
  id UUID PRIMARY KEY,
  symbol TEXT,
  tag TEXT,
  description TEXT,
  score INT DEFAULT 0
);

CREATE TABLE author_forecasts (
  id UUID PRIMARY KEY,
  author_id UUID NOT NULL,
  CONSTRAINT author_key FOREIGN KEY (author_id) REFERENCES authors(id),
  CONSTRAINT forecast_key FOREIGN KEY (id) REFERENCES forecasts(id)
);
