-- description: opens a stage before the up block
-- authoredAt: 1370028292
-- stage: 1
-- TEST ↑LINE_ERROR unexpected '-- stage:' before '-- up:'
-- up:

CREATE TABLE premature_stage (id uuid PRIMARY KEY)
