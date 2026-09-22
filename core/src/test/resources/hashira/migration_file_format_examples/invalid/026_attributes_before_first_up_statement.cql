-- description: has attributes before the first up statement
-- authoredAt: 1370028296
-- up:

-- description: not an up statement
-- TEST ↑LINE_ERROR unexpected '-- description:' after '-- up:'
-- authoredAt: 1370028297
-- TEST ↑LINE_ERROR unexpected '-- authoredAt:' after '-- up:'
-- up:
-- TEST ↑LINE_ERROR unexpected '-- up:' after '-- up:'

CREATE TABLE misplaced_attributes (id uuid PRIMARY KEY)
