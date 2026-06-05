-- CDC smoke test (Postgres side), run against the 'cdc' database.
-- Inserts a marker row into core.foo so CDC replicates it into XTDB's pg_cdc.
-- The marker name carries a timestamp so cdc-test-query can spot the latest one.
INSERT INTO core.foo (name)
VALUES ('cdc-test ' || now())
RETURNING _id, name, created_at;
