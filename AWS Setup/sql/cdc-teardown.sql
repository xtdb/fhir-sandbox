-- Postgres-side CDC teardown, run against the default 'postgres' database.
-- Drops the whole 'cdc' database for a clean slate (cascades the core schema,
-- tables, and the xtdb publication). Run after the XTDB DETACH.

-- Drop the XTDB logical replication slot first if present — a database with a
-- logical replication slot can't be dropped. (XTDB should have released it on
-- DETACH, leaving it inactive.)
SELECT pg_drop_replication_slot('xtdb_cdc')
WHERE EXISTS (SELECT FROM pg_replication_slots WHERE slot_name = 'xtdb_cdc');

-- Terminate any lingering connections to cdc so the drop can proceed.
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = 'cdc' AND pid <> pg_backend_pid();

DROP DATABASE IF EXISTS cdc;
