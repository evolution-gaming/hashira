-- description: creates views table
-- authoredAt: 1370028263
-- up:

CREATE TABLE views (
  id uuid PRIMARY KEY,
  url text
)

-- down:

DROP TABLE views
