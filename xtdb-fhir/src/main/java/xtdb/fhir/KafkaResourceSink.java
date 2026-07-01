package xtdb.fhir;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xtdb.fhir.util.JsonUtil;

import java.util.List;
import java.util.Map;

/**
 * A {@link ResourceSink} that produces Patient resources as JSON messages to a
 * Kafka topic, keyed by {@code _id}. XTDB's {@code !KafkaConnect} external source
 * consumes the topic and indexes each message into the {@code patient} table
 * (see {@code sql/attach-kafka-patient.sql}). Only Patient resources are produced;
 * other resource types in the bundle are ignored.
 */
public final class KafkaResourceSink implements ResourceSink {

  private static final Logger log = LoggerFactory.getLogger(KafkaResourceSink.class);

  // Snake-cased resource type this sink produces (the target table).
  private static final String PATIENT = "patient";

  private final KafkaProducer<String, String> producer;
  private final String topic;
  private final int maxConcurrency;

  public KafkaResourceSink(KafkaProducer<String, String> producer, String topic, int maxConcurrency) {
    this.producer = producer;
    this.topic = topic;
    this.maxConcurrency = maxConcurrency;
  }

  @Override
  public void writeBundle(Map<String, List<JsonNode>> resourcesByType) {
    List<JsonNode> patients = resourcesByType.get(PATIENT);
    if (patients == null || patients.isEmpty()) return;

    for (JsonNode patient : patients) {
      producer.send(new ProducerRecord<>(topic, JsonUtil.getText(patient, "_id"), patient.toString()));
    }
    producer.flush();
    log.debug("produced {} patient resources to {}", patients.size(), topic);
  }

  @Override
  public int maxConcurrency() {
    return maxConcurrency;
  }

  @Override
  public void close() {
    producer.close();
  }
}
