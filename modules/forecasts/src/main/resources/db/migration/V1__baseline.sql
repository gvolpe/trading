CREATE TABLE authors (
  id UUID PRIMARY KEY,
  name VARCHAR UNIQUE NOT NULL,
  website TEXT NULL
);

CREATE TABLE forecasts (
  id UUID PRIMARY KEY,
  author_id UUID NOT NULL,
  CONSTRAINT author_forecasts_key FOREIGN KEY (author_id) REFERENCES authors(id)
);
