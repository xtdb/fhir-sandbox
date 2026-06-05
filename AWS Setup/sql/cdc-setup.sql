-- Postgres-side CDC setup, run against the default 'postgres' database.
-- Creates the 'cdc' database if missing, then switches into it to create the
-- 'core' schema, placeholder tables, and the 'xtdb' publication that XTDB's
-- pg_cdc external source replicates from. Idempotent — safe to re-run.
--
-- NOTE: the table schema here is a placeholder ('foo'/'bar') — the real CDC
-- tables are TBD. 'FOR TABLES IN SCHEMA core' auto-includes any future tables
-- added to the schema (requires PostgreSQL 15+).

-- Create the 'cdc' database only when missing (\gexec runs nothing on no rows).
SELECT 'CREATE DATABASE cdc'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'cdc')\gexec

-- Switch into the cdc database for the schema/table/publication setup.
\connect cdc

CREATE SCHEMA IF NOT EXISTS core;

CREATE TABLE IF NOT EXISTS core.foo (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS core.bar (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    foo_id     bigint REFERENCES core.foo (id),
    value      text,
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Drop-then-create so cdc-start is idempotent (no CREATE PUBLICATION IF NOT EXISTS).
DROP PUBLICATION IF EXISTS xtdb;
CREATE PUBLICATION xtdb FOR TABLES IN SCHEMA core;
