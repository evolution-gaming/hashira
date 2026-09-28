-- description: reopens the up block after down
-- authoredAt: 1370028294
-- up:

CREATE TABLE reopened (id uuid PRIMARY KEY)

-- down:

DROP TABLE reopened

-- up:
-- TEST ↑LINE_ERROR unexpected '-- up:' after '-- down:'

CREATE INDEX reopened_idx ON reopened (id)
