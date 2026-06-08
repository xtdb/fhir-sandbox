-- CDC smoke test (XTDB side), run against the attached 'pg_cdc' database.
-- Shows the most recently replicated core.patient rows so you can confirm the
-- cdc-test-insert marker has landed via CDC.
SELECT _id, resource_type, status, last_updated, created_at
FROM core.patient
ORDER BY created_at DESC
LIMIT 10;
