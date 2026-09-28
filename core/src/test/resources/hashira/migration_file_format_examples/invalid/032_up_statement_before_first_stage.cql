-- description: has an up statement before the first stage
-- authoredAt: 1370028303
-- up:

CREATE TABLE unstaged (id uuid PRIMARY KEY)

-- stage: 2
-- TEST ↑LINE_ERROR CQL statement before the first stage is not in a stage
ALTER TABLE unstaged ADD name text

-- stage: 3
CREATE INDEX unstaged_name_idx ON unstaged (name)
