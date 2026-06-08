-- Postgres-side CDC setup, run against the default 'postgres' database.
-- Creates the 'cdc' database if missing, then switches into it to create the
-- 'core' schema, the resource-type tables the generator writes to, and the 'xtdb'
-- publication that XTDB's pg_cdc external source replicates from. Idempotent —
-- safe to re-run.
--
-- Why pre-create the resource tables here (rather than let the generator's
-- CREATE TABLE IF NOT EXISTS do it after attach): XTDB's initial CDC snapshot
-- only captures tables present in the publication at attach time. A table that
-- joins the publication afterwards has its pre-existing rows silently dropped —
-- only later changes stream (see xtdb/xtdb#5497). Creating the tables here, before
-- `cdc-attach`, puts them in the initial snapshot; the generator then only inserts
-- into already-captured tables. 'FOR TABLES IN SCHEMA core' still auto-includes any
-- other types the generator emits (PG15+), but those are subject to #5497.
--
-- The primary key is named '_id' because XTDB requires an '_id' on every
-- replicated row; it also supplies the replica identity for updates/deletes.

-- Create the 'cdc' database only when missing (\gexec runs nothing on no rows).
SELECT 'CREATE DATABASE cdc'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'cdc')\gexec

-- Switch into the cdc database for the schema/table/publication setup.
\connect cdc

CREATE SCHEMA IF NOT EXISTS core;

-- Resource-type tables, flat schema matching PostgresColumnWriter (a few promoted
-- top-level FHIR fields, no jsonb). The list is the resource types Synthea emits;
-- the generator's DDL no-ops against these. Created before attach so they land in
-- the snapshot (xtdb/xtdb#5497).
DO $$
DECLARE
    t text;
    resource_tables text[] := ARRAY[
        'allergy_intolerance', 'care_plan', 'care_team', 'claim', 'condition',
        'coverage', 'device', 'diagnostic_report', 'encounter',
        'explanation_of_benefit', 'imaging_study', 'immunization',
        'medication_administration', 'medication_request', 'observation',
        'organization', 'patient', 'practitioner', 'procedure',
        'service_request', 'supply_delivery'
    ];
BEGIN
    FOREACH t IN ARRAY resource_tables LOOP
        EXECUTE format($ddl$
            CREATE TABLE IF NOT EXISTS core.%I (
                _id           text PRIMARY KEY,
                resource_type text,
                status        text,
                patient_id    text,
                encounter_id  text,
                code          text,
                last_updated  timestamptz,
                created_at    timestamptz NOT NULL DEFAULT now()
            )$ddl$, t);
    END LOOP;
END $$;

-- Drop-then-create so cdc-start is idempotent (no CREATE PUBLICATION IF NOT EXISTS).
DROP PUBLICATION IF EXISTS xtdb;
CREATE PUBLICATION xtdb FOR TABLES IN SCHEMA core;
