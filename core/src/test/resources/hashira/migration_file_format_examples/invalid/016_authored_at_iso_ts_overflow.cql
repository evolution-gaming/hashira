-- description: authoredAt is an ISO timestamp exceeding 2^63 - 1 milliseconds
-- authoredAt: +292278994-08-17T07:12:55.808Z
-- TEST ↑LINE_ERROR authoredAt invalid value: must be <= 2^63-1 (+292278994-08-17T07:12:55.807Z)
-- up:

CREATE TABLE bad_authored_at (id uuid PRIMARY KEY)
