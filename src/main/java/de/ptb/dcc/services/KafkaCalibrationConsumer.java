package de.ptb.dcc.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ptb.dcc.entities.Calibration;
import de.ptb.dcc.repositories.CalibrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaCalibrationConsumer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaCalibrationConsumer.class);
    private final CalibrationRepository calibrationRepository;
    private final ObjectMapper objectMapper;

    public KafkaCalibrationConsumer(CalibrationRepository calibrationRepository, ObjectMapper objectMapper) {
        this.calibrationRepository = calibrationRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "calibrations")
    public void listen(String message) {
        logger.info("Received calibration message: {}", message);
        try {
            JsonNode root = objectMapper.readTree(message);
            
            Calibration calibration = new Calibration();
            
            if (root.has("mu_id")) {
                calibration.setMuId(root.get("mu_id").asLong());
            } else {
                logger.warn("Missing mu_id in calibration message");
                return;
            }
            
            if (root.has("sub")) {
                calibration.setSub(root.get("sub").asText());
            } else {
                logger.warn("Missing sub in calibration message");
                return;
            }
            
            if (root.has("calibration-data")) {
                calibration.setCalibrationData(root.get("calibration-data").toString());
            } else {
                logger.warn("Missing calibration-data in calibration message");
                return;
            }
            
            if (root.has("description") && !root.get("description").isNull()) {
                calibration.setDescription(root.get("description").asText());
            }
            
            if (root.has("calibration_device_id") && !root.get("calibration_device_id").isNull()) {
                calibration.setCalibrationDeviceId(root.get("calibration_device_id").asText());
            }
            
            calibration.setProcessed(false);
            
            calibrationRepository.save(calibration);
            logger.info("Saved calibration for mu_id: {} with id: {}", calibration.getMuId(), calibration.getId());
            
        } catch (Exception e) {
            logger.error("Error processing calibration message: {}", message, e);
        }
    }
}
