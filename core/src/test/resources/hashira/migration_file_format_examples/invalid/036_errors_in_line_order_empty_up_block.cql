-- description: has an empty up block holding only a misplaced attribute
-- authoredAt: 1370028307
-- up:
-- TEST ↑LINE_ERROR no CQL statements in the up section

-- authoredAt: 1370028308
-- TEST ↑LINE_ERROR unexpected '-- authoredAt:' after '-- up:'

-- down:

DROP TABLE nothing_created
