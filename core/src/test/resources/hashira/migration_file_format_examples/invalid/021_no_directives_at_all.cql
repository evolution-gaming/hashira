-- TEST FILE_ERROR missing required attributes before the up section: authoredAt, description
-- TEST FILE_ERROR missing up statements section
CREATE TABLE plain_cql (id uuid PRIMARY KEY)

DROP TABLE plain_cql
