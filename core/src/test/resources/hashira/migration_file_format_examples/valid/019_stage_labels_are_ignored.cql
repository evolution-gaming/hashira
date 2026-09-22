-- description: rotates counters tables
-- authoredAt: 1370028271
-- up:

-- stage: 2
CREATE TABLE counters_v2 (id uuid PRIMARY KEY, value counter)

-- stage: the label is free text and is ignored
DROP TABLE counters_v1

-- stage:
CREATE INDEX counters_v2_value_idx ON counters_v2 (value)
