package de.ptb.dcc.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.ptb.dcc.entities.CalibratorRequest;
import de.ptb.dcc.repositories.CalibratorRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Consumes raw calibration export messages from 'calibration.request',
 * persists them in the calibrator_request table, runs calibration data
 * processing, and publishes the result to 'calibration.response'.
 *
 * The calibration_request table is created automatically by Hibernate
 * (spring.jpa.hibernate.ddl-auto=update).
 *
 * Kafka wiring (configured in application.properties):
 *   Broker    : ${spring.kafka.bootstrap-servers}
 *   CONSUMING : ${calibration.topic.request}   (group: ${calibration.consumer.group})
 *   PRODUCING : ${calibration.topic.response}
 */
@Service
public class KafkaCalibrationRequestConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaCalibrationRequestConsumer.class);

    @Value("${calibration.topic.response:calibration.response}")
    private String responseTopic;

    private final CalibratorRequestRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaCalibrationRequestConsumer(CalibratorRequestRepository repository,
                                           KafkaTemplate<String, String> kafkaTemplate,
                                           ObjectMapper objectMapper) {
        this.repository    = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper  = objectMapper;
    }

    // ── Kafka listener ───────────────────────────────────────────────────────

    @KafkaListener(
        topics     = "${calibration.topic.request:calibration.request}",
        groupId    = "${calibration.consumer.group:dcc-service-calibration-request}",
        id         = "calibration-request-listener"
    )
    public void listen(String message) {
        log.info("[calibration.request] Received message ({} chars)", message.length());

        try {
            JsonNode root = objectMapper.readTree(message);

            // ── Mandatory fields ─────────────────────────────────────────────
            String calibrationId = parseRequiredString(root, "calibration_id");
            if (calibrationId == null) return;

            Long calibratorId = parseRequiredLong(root, "calibrator_id", calibrationId);
            if (calibratorId == null) return;

            Long muId = parseRequiredLong(root, "mu_id", calibrationId);
            if (muId == null) return;

            Long sensorId = parseRequiredLong(root, "sensor_id", calibrationId);
            if (sensorId == null) return;

            // ── Idempotency: skip if already saved ───────────────────────────
            if (repository.findByCalibrationId(calibrationId).isPresent()) {
                log.warn("[{}] Already in DB – skipping duplicate.", calibrationId);
                return;
            }

            // ── Persist raw request ──────────────────────────────────────────
            CalibratorRequest entity = new CalibratorRequest();
            entity.setCalibrationId(calibrationId);
            entity.setCalibratorId(calibratorId);
            entity.setMuId(muId);
            entity.setSensorId(sensorId);
            entity.setInputJson(message);          // full original JSON
            entity.setProcessed(false);

            entity = repository.save(entity);
            log.info("[{}] Saved to calibrator_request (id={})", calibrationId, entity.getId());

            // ── Process & respond ────────────────────────────────────────────
            calibrationDataProcessing(entity, root);

        } catch (Exception e) {
            log.error("[calibration.request] Unhandled error processing message", e);
        }
    }

    // ── Calibration processing ───────────────────────────────────────────────

    /**
     * Processes a persisted calibration request.
     *
     * Currently produces a MOCKED calibration constants response that mirrors
     * the structure of the Python receiver's constants_to_dict() output.
     * Replace the body of this method with real regression logic when ready.
     *
     * After computing the output:
     *  - saves processed_json_output + sets processed=true in the DB
     *  - publishes the JSON result to 'calibration.response'
     */
    private void calibrationDataProcessing(CalibratorRequest entity, JsonNode root) {
        String calibrationId = entity.getCalibrationId();
        log.info("[{}] ── calibrationDataProcessing START ──────────────────────", calibrationId);
        log.info("[{}]   mu_id={}, sensor_id={}, calibrator_id={}",
                calibrationId, entity.getMuId(), entity.getSensorId(), entity.getCalibratorId());

        // Log step count if present
        if (root.has("steps")) {
            log.info("[{}]   steps={}", calibrationId, root.get("steps"));
        }
        if (root.has("reference_temperature_samples")) {
            log.info("[{}]   reference_temperature_samples count={}",
                    calibrationId, root.get("reference_temperature_samples").size());
        }
        if (root.has("sensor_temperature_samples")) {
            log.info("[{}]   sensor_temperature_samples count={}",
                    calibrationId, root.get("sensor_temperature_samples").size());
        }

        // ── MOCKED output (mirrors Python constants_to_dict structure) ────────
        // TODO: replace with real linear regression over reference & sensor data.
        ObjectNode output = objectMapper.createObjectNode();
        output.put("calibration_id",  calibrationId);
        output.put("calibrator_id",   entity.getCalibratorId());
        output.put("mu_id",           entity.getMuId());
        output.put("sensor_id",       entity.getSensorId());
        output.put("slope",           1.0);
        output.put("intercept",       0.0);
        output.putNull("r_squared");
        ArrayNode steps = output.putArray("step_calibrations");
        // steps remain empty in the mock; real processing would populate them

        String outputJson;
        try {
            outputJson = objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            log.error("[{}] Failed to serialize mocked output", calibrationId, e);
            return;
        }

        log.info("[{}] Mocked output: {}", calibrationId, outputJson);

        // ── Persist result ────────────────────────────────────────────────────
        entity.setProcessedJsonOutput(outputJson);
        entity.setProcessed(true);
        repository.save(entity);
        log.info("[{}] Marked as processed in DB", calibrationId);

        // ── Publish to calibration.response ───────────────────────────────────
        kafkaTemplate.send(responseTopic, outputJson)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[{}] Failed to send response to {}", calibrationId, responseTopic, ex);
                    } else {
                        log.info("[{}] Response sent → topic={} partition={} offset={}",
                                calibrationId,
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });

        log.info("[{}] ── calibrationDataProcessing END ────────────────────────", calibrationId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String parseRequiredString(JsonNode root, String field) {
        if (!root.has(field) || root.get(field).isNull()) {
            log.warn("[calibration.request] Missing required field '{}' – skipping.", field);
            return null;
        }
        return root.get(field).asText();
    }

    private Long parseRequiredLong(JsonNode root, String field, String calibrationId) {
        if (!root.has(field) || root.get(field).isNull()) {
            log.warn("[{}] Missing required field '{}' – skipping.", calibrationId, field);
            return null;
        }
        return root.get(field).asLong();
    }
}
