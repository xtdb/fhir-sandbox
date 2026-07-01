-- Kafka external-source smoke test (XTDB side), run against the attached
-- 'kafka_src' database. Shows the most recently indexed 'patient' rows so you can
-- confirm the kafka-src-test-insert marker has landed via the !KafkaConnect source.
SELECT *
FROM patient
ORDER BY _id DESC
LIMIT 10;
