-- description: creates audit table


-- authoredAt: 1370028272

-- up:


CREATE TABLE audit (

  id uuid PRIMARY KEY,

  actor text

)


-- down:


DROP TABLE audit

