-- description: opens the down block first
-- authoredAt: 1370028293
-- down:
-- TEST ↑LINE_ERROR unexpected '-- down:' before '-- up:'

DROP TABLE inverted

-- up:

CREATE TABLE inverted (id uuid PRIMARY KEY)
