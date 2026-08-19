-- Kafka external-source smoke test (XTDB side), run against the attached
-- 'kafka_src' database. Shows the most recently indexed 'patient' rows so you can
-- confirm the kafka-src-test-insert marker has landed via the !KafkaConnect source.
-- Uses bitemporal resolution to resolve only the last 5 minutes of system-time,
-- rather than ordering the whole table by _system_from (a full scan + sort).
SELECT *
FROM patient FOR SYSTEM_TIME FROM (NOW() - INTERVAL 'PT5M') TO NOW()
LIMIT 10;
