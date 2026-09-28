-- TEST FILE_ERROR missing up statements section
-- description: has a down block but no up block
-- authoredAt: 1370028309

CREATE TABLE never_created (id uuid PRIMARY KEY)

-- down:
-- TEST ↑LINE_ERROR unexpected '-- down:' before '-- up:'

DROP TABLE never_created
