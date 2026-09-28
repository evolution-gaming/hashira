-- description: drops api_keys secret column
-- authoredAt: +01370028268
-- up:

ALTER TABLE api_keys
DROP secret
