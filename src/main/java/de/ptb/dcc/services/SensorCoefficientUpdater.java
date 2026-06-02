package de.ptb.dcc.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ptb.dcc.entities.Sensor;
import de.ptb.dcc.repositories.SensorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Separate bean to persist sensor calibration coefficients in a fresh transaction
 * (REQUIRES_NEW). The parent transaction is held open for the duration of the Python
 * subprocess and may time out; this ensures the sensor update commits independently.
 */
@Service
public class SensorCoefficientUpdater {

    private static final Logger log = LoggerFactory.getLogger(SensorCoefficientUpdater.class);

    private final SensorRepository sensorRepo;

    public SensorCoefficientUpdater(SensorRepository sensorRepo) {
        this.sensorRepo = sensorRepo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void update(Long sensorId, String resultJson) {
        Sensor sensor = sensorRepo.findById(sensorId).orElse(null);
        if (sensor == null) {
            log.warn("[SensorCoeffUpdater] Sensor not found: id={}", sensorId);
            return;
        }
        doUpdate(sensor, resultJson);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void update(Sensor sensor, String resultJson) {
        Sensor fresh = sensorRepo.findById(sensor.getId()).orElse(null);
        if (fresh == null) {
            log.warn("[SensorCoeffUpdater] Sensor not found: id={}", sensor.getId());
            return;
        }
        doUpdate(fresh, resultJson);
    }

    private void doUpdate(Sensor sensor, String resultJson) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(resultJson);
            JsonNode calibResult = root.path("_calibration_result");
            if (calibResult.isMissingNode()) {
                log.warn("[SensorCoeffUpdater] _calibration_result missing from resultJson");
                return;
            }

            String model = calibResult.path("_calib_model").asText("");
            Double newA = null, newB = null, newC = null, newD = null;

            switch (model) {
                case "linear", "linear_interp" -> {
                    newA = jsonDouble(calibResult, "_A");
                    newB = jsonDouble(calibResult, "_B");
                    newC = 0.0;
                    newD = 0.0;
                }
                case "cubic", "cubic_interp" -> {
                    newA = jsonDouble(calibResult, "_a0");
                    newB = jsonDouble(calibResult, "_a1");
                    newC = jsonDouble(calibResult, "_a2");
                    newD = jsonDouble(calibResult, "_a3");
                }
                case "cube-log" -> {
                    newA = jsonDouble(calibResult, "_C0");
                    newB = jsonDouble(calibResult, "_C1");
                    newC = jsonDouble(calibResult, "_C3");
                    newD = 0.0;
                }
                default -> {
                    log.warn("[SensorCoeffUpdater] Unknown calib model '{}'", model);
                    return;
                }
            }

            if (newA == null || newB == null) {
                log.warn("[SensorCoeffUpdater] Could not extract A/B for model '{}'", model);
                return;
            }

            sensor.setCoeffA(newA);
            sensor.setCoeffB(newB);
            sensor.setCoeffC(newC);
            sensor.setCoeffD(newD);
            sensor.setCalDate(Instant.now().toEpochMilli());
            sensorRepo.save(sensor);

            log.info("[SensorCoeffUpdater] Sensor {} coefficients saved: model={} A={} B={} C={} D={}",
                    sensor.getId(), model, newA, newB, newC, newD);

        } catch (Exception e) {
            log.warn("[SensorCoeffUpdater] Failed: {}", e.getMessage(), e);
        }
    }

    private static Double jsonDouble(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return n.isNumber() ? n.doubleValue() : null;
    }
}
