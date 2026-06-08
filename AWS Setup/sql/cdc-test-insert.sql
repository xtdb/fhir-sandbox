-- CDC smoke test (Postgres side), run against the 'cdc' database.
-- Inserts a marker Patient row into core.patient so CDC replicates it into XTDB's
-- pg_cdc. The status carries a timestamp so cdc-test-query can spot the latest one.
INSERT INTO core.patient (_id, resource_type, status, last_updated)
VALUES (gen_random_uuid()::text, 'patient', 'cdc-test ' || now(), now())
RETURNING _id, resource_type, status, last_updated, created_at;
