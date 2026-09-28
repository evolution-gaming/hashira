-- description: authoredAt is zero
-- authoredAt: 0
-- TEST ↑LINE_ERROR authoredAt invalid value: must be >= 1 (1970-01-01T00:00:00.001Z)
-- up:

CREATE TABLE bad_authored_at (id uuid PRIMARY KEY)
