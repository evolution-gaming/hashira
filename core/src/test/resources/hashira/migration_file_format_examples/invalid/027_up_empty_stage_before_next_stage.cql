-- description: has an empty up stage followed by another stage
-- authoredAt: 1370028298
-- up:

-- stage: 1
-- TEST ↑LINE_ERROR no CQL statements in the stage

-- stage: 2
CREATE TABLE empty_first_stage (id uuid PRIMARY KEY)
