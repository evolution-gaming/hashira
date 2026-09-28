-- description: duplicate authoredAt
-- authoredAt: 1370028281
-- authoredAt: 2
-- TEST ↑LINE_ERROR authoredAt already defined
-- up:

CREATE TABLE duplicate_authored_at (id uuid PRIMARY KEY)
