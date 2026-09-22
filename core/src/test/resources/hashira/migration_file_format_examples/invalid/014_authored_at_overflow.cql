-- description: authoredAt exceeds 2^63 - 1
-- authoredAt: 9223372036854775808
-- TEST ↑LINE_ERROR authoredAt invalid value: must be <= 2^63-1 (+292278994-08-17T07:12:55.807Z)
-- up:

CREATE TABLE bad_authored_at (id uuid PRIMARY KEY)
