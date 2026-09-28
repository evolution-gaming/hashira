-- description: has no authoredAt
-- up:
-- TEST ↑LINE_ERROR missing required attributes before the up section: authoredAt

CREATE TABLE undated (id uuid PRIMARY KEY)
