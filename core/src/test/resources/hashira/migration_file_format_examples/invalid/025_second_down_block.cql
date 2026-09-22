-- description: opens two down blocks
-- authoredAt: 1370028295
-- up:

CREATE TABLE twice_reversed (id uuid PRIMARY KEY)

-- down:

TRUNCATE twice_reversed

-- down:
-- TEST ↑LINE_ERROR unexpected '-- down:' after '-- down:'

DROP TABLE twice_reversed
