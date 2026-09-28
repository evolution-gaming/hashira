-- description: backfills sessions from logins
-- authoredAt: 1469630067000
-- up:

-- stage: 1
CREATE TABLE sessions_by_user (
  user_id uuid,
  started_at timeuuid,
  PRIMARY KEY (user_id, started_at)
)

-- stage: 2
ALTER TABLE sessions_by_user
ADD ip inet

-- stage: 3
CREATE INDEX sessions_by_user_ip_idx ON sessions_by_user (ip)
