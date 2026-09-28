-- description: has an empty last up stage followed by down
-- authoredAt: 1370028299
-- up:

-- stage: 1
CREATE TABLE empty_last_up_stage (id uuid PRIMARY KEY)

-- stage: 2
-- TEST ↑LINE_ERROR no CQL statements in the stage

-- down:

DROP TABLE empty_last_up_stage
