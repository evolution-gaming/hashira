-- description: allow CQL line comments between up/down and its first stage
-- authoredAt: 1713131542000

-- up:
-- new default session TTL: 12 months = 31536000 sec

-- stage: 1
ALTER TABLE IF EXISTS session WITH default_time_to_live = 31536000;

-- down:

-- old default session TTL: 6 months = 15768000 sec

-- stage: 1

ALTER TABLE IF EXISTS session WITH default_time_to_live = 15768000;
