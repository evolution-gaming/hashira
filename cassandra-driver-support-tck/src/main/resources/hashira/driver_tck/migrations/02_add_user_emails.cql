-- description: add user emails
-- authoredAt: 2026-10-02T10:00:00Z

-- up:

-- stage: 1
ALTER TABLE users
    ADD email text;

-- stage: 2
INSERT INTO users (id, name, email)
VALUES ('u1', 'User One', 'u1@example.com');

-- down:

-- stage: 1
DELETE
FROM users
WHERE id = 'u1';

-- stage: 2
ALTER TABLE users
    DROP email;
