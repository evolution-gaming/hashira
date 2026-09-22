-- description: has an up statement before the first stage, and an empty stage later
-- authoredAt: 1370028304
-- up:

CREATE TABLE unstaged_then_empty (id uuid PRIMARY KEY)

-- stage: 2
-- TEST ↑LINE_ERROR CQL statement before the first stage is not in a stage
ALTER TABLE unstaged_then_empty ADD name text

-- stage: 3
-- TEST ↑LINE_ERROR no CQL statements in the stage

-- stage: 4
CREATE INDEX unstaged_then_empty_name_idx ON unstaged_then_empty (name)
