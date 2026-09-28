-- description: authoredAt is an ISO timestamp with sub-millisecond precision
-- authoredAt: 2013-05-31T18:47:42.1111Z
-- TEST ↑LINE_ERROR authoredAt invalid value: ISO timestamp string must have millisecond precision, sub-millisecond values not supported
-- up:

CREATE TABLE bad_authored_at (id uuid PRIMARY KEY)
