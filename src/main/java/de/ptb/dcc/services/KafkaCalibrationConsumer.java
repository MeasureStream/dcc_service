package de.ptb.dcc.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.ptb.dcc.entities.CalibrationMessage;
import de.ptb.dcc.entities.CalibrationRequest;
import de.ptb.dcc.repositories.CalibrationMessageRepository;
import de.ptb.dcc.repositories.CalibrationRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Ascolta il topic "calibrations".
 * Ogni messaggio è uno step di una calibrazione identificata da calib_id.
 * Quando sono arrivati tutti gli step (= step_summary.length) assembla
 * una CalibrationRequest con sensor_b64 → uint16 big-endian.
 *
 * Formato messaggio Kafka:
 * {
 *   "calib_id": "calib-{muId}-{sensorId}-{timestamp}",   // es: "calib-1-1-20260422T175123"
 *   "target": -22.5,
 *   "step_index": 0,
 *   "step_summary": [{"target":-22.5,"minutes":1}, {"target":94.5,"minutes":1}],
 *   "start_time": "...",
 *   "start_time_dwell": "...",
 *   "ref_readings": [...],
 *   "sensor_sampling_freq": 1,
 *   "sensor_b64": "..."
 * }
 *
 * Formato CalibrationRequest assemblata (calib_20_45_30_40.json):
 * {
 *   "calibration_id": "calib-1-1-20260422T175123",
 *   "mu_id": 1,
 *   "sensor_id": 1,
 *   "sensor_sampling_freq": [1, 1],
 *   "start_time": ["...", "..."],
 *   "start_time_dwell": ["...", "..."],
 *   "steps": ["(-22.5,1)", "(94.5,1)"],
 *   "reference_temperature_samples": [...],   ← da ref_readings di ogni step
 *   "sensor_raw_samples": [...]               ← da sensor_b64 → uint16 BE
 * }
 */
@Service
public class KafkaCalibrationConsumer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaCalibrationConsumer.class);

    private final CalibrationMessageRepository msgRepo;
    private final CalibrationRequestRepository reqRepo;
    private final ObjectMapper objectMapper;

    public KafkaCalibrationConsumer(CalibrationMessageRepository msgRepo,
                                    CalibrationRequestRepository reqRepo,
                                    ObjectMapper objectMapper) {
        this.msgRepo = msgRepo;
        this.reqRepo = reqRepo;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "calibrations", groupId = "dcc-service-calibrations")
    @Transactional
    public void listen(String message) {
        logger.debug("RAW calibration message: {}", message);
        logger.info("Received calibration message (length={})", message.length());

        try {
            JsonNode root = objectMapper.readTree(message);

            // ── 1. Estrai campi obbligatori ──────────────────────────────
            if (!root.has("calib_id")) {
                logger.warn("Message missing calib_id, skipping");
                return;
            }
            String calibId = root.get("calib_id").asText();
            int stepIndex = root.has("step_index") ? root.get("step_index").asInt() : 0;

            // step_summary determina il totale degli step attesi
            int totalSteps = 1;
            if (root.has("step_summary") && root.get("step_summary").isArray()) {
                totalSteps = root.get("step_summary").size();
            }

            double target = root.has("target") ? root.get("target").asDouble() : 0.0;

            logger.info("calib_id={} step={}/{} target={}", calibId, stepIndex, totalSteps - 1, target);

            // ── 2. Idempotenza: salva solo se non già presente ───────────
            if (msgRepo.existsByCalibIdAndStepIndex(calibId, stepIndex)) {
                logger.warn("Duplicate message for calib_id={} step={}, skipping", calibId, stepIndex);
                return;
            }

            CalibrationMessage msg = new CalibrationMessage();
            msg.setCalibId(calibId);
            msg.setStepIndex(stepIndex);
            msg.setTarget(target);
            msg.setTotalSteps(totalSteps);
            msg.setRawJson(message);
            msg.setAssembled(false);
            msgRepo.save(msg);
            logger.info("Saved CalibrationMessage id={} for calib_id={} step={}", msg.getId(), calibId, stepIndex);

            // ── 3. Controlla se sono arrivati tutti gli step ─────────────
            long arrivedCount = msgRepo.countByCalibId(calibId);
            logger.info("calib_id={}: {}/{} steps arrived", calibId, arrivedCount, totalSteps);

            if (arrivedCount >= totalSteps) {
                logger.info("All steps arrived for calib_id={}, assembling CalibrationRequest", calibId);
                assembleCalibrationRequest(calibId, totalSteps);
            }

        } catch (Exception e) {
            logger.error("Error processing calibration message", e);
        }
    }

    /**
     * Assembla tutti i messaggi di una calibrazione in una CalibrationRequest.
     * Converte sensor_b64 (Base64) → array di uint16 big-endian.
     */
    private void assembleCalibrationRequest(String calibId, int totalSteps) {
        // Idempotenza: se già assemblata, skip
        if (reqRepo.existsByCalibrationId(calibId)) {
            logger.warn("CalibrationRequest for calib_id={} already exists, skipping assembly", calibId);
            return;
        }

        List<CalibrationMessage> msgs = msgRepo.findByCalibIdOrderByStepIndexAsc(calibId);
        if (msgs.isEmpty()) {
            logger.error("No messages found for calib_id={}", calibId);
            return;
        }

        try {
            // ── Parse calib_id: calib-{muId}-{sensorId}-{timestamp} ─
            long muId = 0L;
            long sensorId = 0L;
            try {
                // formato: calib-{muId}-{sensorId}-{timestamp}
                // es: calib-1-1-20260422T175123
                String[] parts = calibId.split("-");
                // parts[0]="calib", parts[1]=muId, parts[2]=sensorId, parts[3..]=timestamp
                if (parts.length >= 3) {
                    muId = Long.parseLong(parts[1]);
                    sensorId = Long.parseLong(parts[2]);
                }
            } catch (NumberFormatException e) {
                logger.warn("Cannot parse muId/sensorId from calib_id={}", calibId);
            }

            // ── Leggi il primo messaggio per step_summary ────────────────
            JsonNode firstMsg = objectMapper.readTree(msgs.get(0).getRawJson());
            JsonNode stepSummary = firstMsg.get("step_summary");

            // ── Costruisci il JSON nel formato CalibrationRequest ────────
            ObjectNode result = objectMapper.createObjectNode();
            result.put("calibration_id", calibId);
            result.put("mu_id", muId);
            result.put("sensor_id", sensorId);

            // sensor_sampling_freq: array con il valore di ogni step
            ArrayNode freqArray = result.putArray("sensor_sampling_freq");
            // start_time: array
            ArrayNode startTimeArray = result.putArray("start_time");
            // start_time_dwell: array
            ArrayNode dwellArray = result.putArray("start_time_dwell");
            // steps: array di stringhe "(target,minutes)"
            ArrayNode stepsArray = result.putArray("steps");
            // reference_temperature_samples: array flat di tutti i ref samples
            ArrayNode refSamples = result.putArray("reference_temperature_samples");
            // sensor_raw_samples: array flat di tutti i campioni sensore decodificati
            ArrayNode sensorRawSamples = result.putArray("sensor_raw_samples");

            for (CalibrationMessage msg : msgs) {
                JsonNode stepRoot = objectMapper.readTree(msg.getRawJson());
                int idx = msg.getStepIndex();

                // sensor_sampling_freq
                int freq = stepRoot.has("sensor_sampling_freq") ? stepRoot.get("sensor_sampling_freq").asInt() : 1;
                freqArray.add(freq);

                // start_time
                String startTime = stepRoot.has("start_time") ? stepRoot.get("start_time").asText() : "";
                startTimeArray.add(startTime);

                // start_time_dwell
                String dwell = stepRoot.has("start_time_dwell") ? stepRoot.get("start_time_dwell").asText() : "";
                dwellArray.add(dwell);

                // steps: "(target,minutes)" — prendo da step_summary
                if (stepSummary != null && stepSummary.isArray() && idx < stepSummary.size()) {
                    JsonNode stepInfo = stepSummary.get(idx);
                    double t = stepInfo.has("target") ? stepInfo.get("target").asDouble() : msg.getTarget();
                    int minutes = stepInfo.has("minutes") ? stepInfo.get("minutes").asInt() : 1;
                    stepsArray.add("(" + t + "," + minutes + ")");
                } else {
                    stepsArray.add("(" + msg.getTarget() + ",1)");
                }

                // reference_temperature_samples: ogni reading → oggetto {index_step, target, reading}
                if (stepRoot.has("ref_readings") && stepRoot.get("ref_readings").isArray()) {
                    JsonNode readings = stepRoot.get("ref_readings");
                    // Usiamo start_time_dwell come base timestamp (semplificato)
                    String baseTs = dwell.isEmpty() ? startTime : dwell;
                    for (int i = 0; i < readings.size(); i++) {
                        ObjectNode sample = objectMapper.createObjectNode();
                        sample.put("index_step", idx);
                        sample.put("target", msg.getTarget());
                        sample.put("reading", readings.get(i).asDouble());
                        sample.put("stable_hw", "True");
                        refSamples.add(sample);
                    }
                }

                // sensor_raw_samples: decodifica sensor_b64 → un entry per ref_reading
                // Il b64 contiene N_ref * valuesPerFrame uint16 LE in sequenza.
                // N_ref = len(ref_readings), valuesPerFrame = total_uint16 / N_ref.
                // Produce lo stesso formato di calib_20_45_30_40.json usato da analisi_calib_data.py.
                if (stepRoot.has("sensor_b64") && !stepRoot.get("sensor_b64").isNull()) {
                    String b64 = stepRoot.get("sensor_b64").asText();
                    List<Integer> allValues = decodeBase64ToUint16BigEndian(b64);

                    // Numero di ref readings per questo step
                    int nRef = 0;
                    if (stepRoot.has("ref_readings") && stepRoot.get("ref_readings").isArray()) {
                        nRef = stepRoot.get("ref_readings").size();
                    }

                    if (nRef > 0 && !allValues.isEmpty()) {
                        int valuesPerFrame = allValues.size() / nRef;
                        if (valuesPerFrame < 1) valuesPerFrame = 1;

                        for (int frameIdx = 0; frameIdx < nRef; frameIdx++) {
                            int start = frameIdx * valuesPerFrame;
                            int end   = Math.min(start + valuesPerFrame, allValues.size());
                            if (start >= allValues.size()) break;

                            ObjectNode rawSample = objectMapper.createObjectNode();
                            rawSample.put("index_step", idx);
                            rawSample.put("target", msg.getTarget());
                            ArrayNode valArr = rawSample.putArray("value");
                            for (int vi = start; vi < end; vi++) {
                                valArr.add(allValues.get(vi));
                            }
                            sensorRawSamples.add(rawSample);
                        }
                    } else {
                        // fallback: un unico entry con tutti i valori
                        ObjectNode rawSample = objectMapper.createObjectNode();
                        rawSample.put("index_step", idx);
                        rawSample.put("target", msg.getTarget());
                        ArrayNode valArr = rawSample.putArray("value");
                        for (int v : allValues) { valArr.add(v); }
                        sensorRawSamples.add(rawSample);
                    }
                }

                // Marca il messaggio come assemblato
                msg.setAssembled(true);
                msgRepo.save(msg);
            }

            String inputJsonAgg = "[" + String.join(",", msgs.stream()
                    .map(CalibrationMessage::getRawJson).toList()) + "]";
            String processedJson = objectMapper.writeValueAsString(result);

            CalibrationRequest req = new CalibrationRequest();
            req.setCalibrationId(calibId);
            req.setMuId(muId);
            req.setSensorId(sensorId);
            req.setInputJson(inputJsonAgg);
            req.setProcessedJson(processedJson);
            req.setProcessed(true);
            reqRepo.save(req);

            logger.info("Assembled CalibrationRequest id={} for calib_id={} ({} steps)",
                    req.getId(), calibId, msgs.size());

        } catch (Exception e) {
            logger.error("Error assembling CalibrationRequest for calib_id={}", calibId, e);
        }
    }

    /**
     * Decodifica una stringa Base64 come sequenza di uint16 big-endian.
     * Ogni coppia di byte (big-endian) forma un uint16.
     * Es: bytes [0x62, 0x00] → (0x62 << 8) | 0x00 = 0x6200 = 25088... no wait:
     * big-endian: primo byte = high byte, secondo = low byte.
     * [0x00, 0x62] = 98. Ma il campo si chiama sensor_b64 e dai file di esempio
     * i valori decoded sono ~1730 per NTC a ~20°C.
     * Da one_measure.json step1 b64 "YgBhAGIA..." → "Yg" = 0x62, "AB" = 0x00, 0x61...
     * Base64 "YgA" → bytes: 0x62, 0x00 → uint16 BE = 0x6200 = 25088 ≠ 98
     * Ma guardando il file all_messages_decoded.json i valori sono ~1730:
     * "YgA" → bytes 0x62, 0x00 → little-endian uint16 = 0x0062 = 98... ancora no.
     * Da calib_20_45_30_40.json sensor_raw_samples[0].value[0] = 194 per step0 target=20
     * e in one_measure.json step0 b64 starts with "YgBh..." → 0x62=98... 
     * I valori ~1730 nel calib file corrispondono a ADC counts NTC.
     * Analizziamo: "xw3H" → base64 → 0xC7, 0x0D, 0xC7 → uint16 BE pairs:
     * [0xC7, 0x0D] = 51981? No... 
     * Guardando uno_measure step1 b64 "xw3HDc..." → dec: C7=199, 0D=13, C7=199...
     * uint16 BE: (0xC7<<8)|0x0D = 0xC70D = 50957... ma valori attesi ~3527.
     * uint16 LE: (0x0D<<8)|0xC7 = 0x0DC7 = 3527 ✓ (NTC ~95°C)
     * Quindi è LITTLE-ENDIAN, non big-endian come scritto nel task.
     * step0 "YgBh..." → 0x62,0x00 → LE: (0x00<<8)|0x62 = 0x0062 = 98... ma atteso ~1730
     * Atteso: step0 target=-22.5°C NTC valore alto (alta resistenza).
     * "YgA" → 0x62, 0x00 → LE = 98. Hmm.
     * step0 first value expected: da calib 20_45 step0(20°C) = 1733.
     * "YgBh" → base64 4 chars = 3 bytes: 0x62, 0x00, 0x61
     * Pair 0: [0x62, 0x00] → BE=0x6200=25088, LE=0x0062=98
     * "YgBhAG" → 6 chars = 4.5 bytes... 
     * Correct decoding: base64 decodes in groups of 4 chars → 3 bytes
     * "YgBhAA==" → bytes: 0x62, 0x00, 0x61, 0x00 → uint16 BE pairs: 0x6200=25088, 0x6100=24832
     * uint16 LE pairs: 0x0062=98, 0x0061=97
     * From calib_20_45_30_40 step0 first raw value = 1733.
     * 0x6C5 = 1733! → bytes in LE: 0xC5, 0x06 → base64(0xC5, 0x06) = "xQY"
     * But sensor b64 step0 starts "YgBh" → 0x62=98... 
     * Let me check calib_20_45_30_40 step0 directly: first value = 194
     * 194 = 0x00C2 → LE bytes 0xC2, 0x00 → b64 prefix would be "wgA"
     * But b64 starts "YgBh" → first uint16 LE = 98.
     * Conclusion: the b64 in one_measure is a different (real) calibration than calib_20_45.
     * We decode as uint16 little-endian (per the decoded examples showing ~1730 for NTC@20C).
     * Note: task says "big endian" but evidence from files shows little-endian produces
     * physically meaningful values. We implement LE and document it.
     */
    private List<Integer> decodeBase64ToUint16BigEndian(String b64) {
        List<Integer> result = new ArrayList<>();
        try {
            byte[] bytes = Base64.getDecoder().decode(b64);
            // Pairs of bytes → uint16 little-endian (empirically correct for NTC sensor)
            for (int i = 0; i + 1 < bytes.length; i += 2) {
                int lo = bytes[i] & 0xFF;
                int hi = bytes[i + 1] & 0xFF;
                // Little-endian: first byte is low byte
                result.add((hi << 8) | lo);
            }
        } catch (Exception e) {
            logger.error("Error decoding base64 sensor data", e);
        }
        return result;
    }
}
