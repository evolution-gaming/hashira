-- description: has an empty stage holding only a misplaced attribute
-- authoredAt: 1370028306
-- up:

-- stage: 1
-- TEST ↑LINE_ERROR no CQL statements in the stage
-- description: not an up statement
-- TEST ↑LINE_ERROR unexpected '-- description:' after '-- up:'

-- stage: 2
CREATE TABLE misplaced_in_empty_stage (id uuid PRIMARY KEY)
