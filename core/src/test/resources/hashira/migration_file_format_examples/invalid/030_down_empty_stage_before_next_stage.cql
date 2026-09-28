-- description: has an empty down stage followed by another stage
-- authoredAt: 1370028301
-- up:

CREATE TABLE empty_down_stage (id uuid PRIMARY KEY)

-- down:

-- stage: 1
-- TEST ↑LINE_ERROR no CQL statements in the stage

-- stage: 2
DROP TABLE empty_down_stage
