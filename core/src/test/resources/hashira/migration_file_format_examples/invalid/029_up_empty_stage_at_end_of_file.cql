-- description: has an empty last up stage at the end of the file
-- authoredAt: 1370028300
-- up:

-- stage: 1
CREATE TABLE empty_trailing_stage (id uuid PRIMARY KEY)

-- stage: 2
-- TEST ↑LINE_ERROR no CQL statements in the stage
