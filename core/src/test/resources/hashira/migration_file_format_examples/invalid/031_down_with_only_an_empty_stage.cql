-- description: has a down block whose only stage is empty
-- authoredAt: 1370028302
-- up:

CREATE TABLE falsely_reversible (id uuid PRIMARY KEY)

-- down:

-- stage: 1
-- TEST ↑LINE_ERROR no CQL statements in the stage
