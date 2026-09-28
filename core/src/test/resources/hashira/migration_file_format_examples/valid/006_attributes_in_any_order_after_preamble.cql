-- Ticket: ABC-1234
-- Reviewed by the schema owners before merge.
THIS IS NOT CQL AND IS DISCARDED, LIKE EVERY LINE BEFORE "-- up:"

-- authoredAt: 1370028265
-- description: adds index on views url
-- up:

CREATE INDEX views_url_idx ON views (url)

-- down:

DROP INDEX views_url_idx
