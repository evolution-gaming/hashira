-- description: has a down statement before the first stage, and an empty last stage
-- authoredAt: 1370028305
-- up:

-- stage: 1
CREATE TABLE unstaged_down (id uuid PRIMARY KEY, name text)

-- stage: 2
CREATE INDEX unstaged_down_name_idx ON unstaged_down (name)

-- down:

DROP INDEX unstaged_down_name_idx

-- stage: 2
-- TEST ↑LINE_ERROR CQL statement before the first stage is not in a stage
DROP TABLE unstaged_down

-- stage: 3
-- TEST ↑LINE_ERROR no CQL statements in the stage
