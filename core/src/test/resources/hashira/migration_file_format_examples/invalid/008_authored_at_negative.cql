-- description: authoredAt is negative
-- authoredAt: -1370028263
-- TEST ↑LINE_ERROR authoredAt invalid value: must be >= 1 (1970-01-01T00:00:00.001Z)
-- up:

CREATE TABLE bad_authored_at (id uuid PRIMARY KEY)
