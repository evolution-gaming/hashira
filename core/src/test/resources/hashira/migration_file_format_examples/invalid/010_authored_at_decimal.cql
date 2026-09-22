-- description: authoredAt is a decimal
-- authoredAt: 1370028263.0
-- TEST ↑LINE_ERROR authoredAt invalid value: must be a number of milliseconds since UNIX epoch or an ISO timestamp string such as 2007-12-03T10:15:30.00Z
-- up:

CREATE TABLE bad_authored_at (id uuid PRIMARY KEY)
