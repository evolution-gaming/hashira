-- description: create users table
-- authoredAt: 2026-10-01T10:00:00Z

-- up:

CREATE TABLE users
(
    id   text PRIMARY KEY,
    name text
);

-- down:

DROP TABLE users;
