-- CDC smoke test (XTDB side), run against the attached 'pg_cdc' database.
-- Shows the most recently replicated core.foo rows so you can confirm the
-- cdc-test-insert marker has landed via CDC.
SELECT _id, name, created_at
FROM core.foo
ORDER BY created_at DESC
LIMIT 10;
