-- CDC smoke test (XTDB side), run against the attached 'pg_cdc' database.
-- Shows the most recently replicated core.patient rows so you can confirm the
-- cdc-test-insert marker has landed via CDC.
-- Uses bitemporal resolution to resolve only the last 5 minutes of system-time,
-- rather than ordering the whole table by created_at (a full scan + sort).
SELECT _id, resource_type, status, last_updated, created_at
FROM core.patient FOR SYSTEM_TIME FROM (NOW() - INTERVAL 'PT5M') TO NOW()
ORDER BY created_at DESC
LIMIT 10;
