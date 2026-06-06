package de.ptb.dcc.services;

import de.ptb.dcc.dtos.CalibrationDto;
import de.ptb.dcc.dtos.WizardStepRequest;
import de.ptb.dcc.entities.Calibration;
import de.ptb.dcc.entities.CalibrationRequest;
import de.ptb.dcc.entities.Sensor;
import de.ptb.dcc.repositories.CalibrationRepository;
import de.ptb.dcc.repositories.CalibrationRequestRepository;
import de.ptb.dcc.repositories.SensorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class CalibrationWizardService {

    private static final Logger log = LoggerFactory.getLogger(CalibrationWizardService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CalibrationRepository calibrationRepo;
    private final CalibrationRequestRepository requestRepo;
    private final SensorRepository sensorRepo;
    private final PythonBridgeService pythonBridge;
    private final CalibrationWizardInserter inserter;

    public CalibrationWizardService(CalibrationRepository calibrationRepo,
                                     CalibrationRequestRepository requestRepo,
                                     SensorRepository sensorRepo,
                                     PythonBridgeService pythonBridge,
                                     CalibrationWizardInserter inserter) {
        this.calibrationRepo = calibrationRepo;
        this.requestRepo = requestRepo;
        this.sensorRepo = sensorRepo;
        this.pythonBridge = pythonBridge;
        this.inserter = inserter;
    }

    /**
     * Init wizard: trova la Calibration esistente per questa CalibrationRequest,
     * oppure la crea via inserter (REQUIRES_NEW, gestisce duplicati).
     * All steps are pre-filled with classpath templates.
     * Job JSON is populated with sensor model_name and serial_number.
     */
    public CalibrationDto initWizard(Long calibrationRequestId, String userSub) {
        CalibrationRequest req = requestRepo.findById(calibrationRequestId)
                .orElseThrow(() -> new RuntimeException("CalibrationRequest not found: " + calibrationRequestId));

        final Long sensorId = req.getSensorId();
        final String modelName = sensorId != null
                ? sensorRepo.findById(sensorId).map(Sensor::getModelName).orElse(null)
                : null;

        CalibrationDto dto = calibrationRepo.findByCalibrationRequestId(calibrationRequestId)
                .map(this::toDto)
                .orElseGet(() -> {
                    Calibration calib = new Calibration();
                    calib.setCalibrationRequestId(calibrationRequestId);
                    calib.setMuId(req.getMuId() != null ? req.getMuId() : 0L);
                    calib.setSub(userSub);
                    calib.setCalibrationData("{}");
                    calib.setBaseInputJson(loadBaseInput());
                    calib.setCalibrationMethodJson(loadCalibrationMethodTemplate());
                    calib.setMeasurestreamCompanyJson(loadMeasurestreamCompanyTemplate());
                    calib.setClientCompanyJson(loadClientCompanyTemplate());
                    calib.setJobJson(generateJobJson(req, sensorId, modelName));

                    return toDto(inserter.findOrInsert(calibrationRequestId, calib));
                });

        dto.setSensorId(sensorId);
        dto.setSensorModelName(modelName);
        return dto;
    }

    @Transactional
    public CalibrationDto saveStep(Long calibrationId, WizardStepRequest req) {
        Calibration calib = calibrationRepo.findById(calibrationId)
                .orElseThrow(() -> new RuntimeException("Calibration not found: " + calibrationId));

        switch (req.getStep()) {
            case 0 -> calib.setBaseInputJson(req.getJsonData());
            case 1 -> calib.setCalibrationMethodJson(req.getJsonData());
            case 2 -> calib.setMeasurestreamCompanyJson(req.getJsonData());
            case 3 -> calib.setClientCompanyJson(req.getJsonData());
            case 4 -> calib.setJobJson(req.getJsonData());
            default -> throw new IllegalArgumentException("Invalid wizard step: " + req.getStep());
        }

        return toDto(calibrationRepo.save(calib));
    }

    @Transactional
    public CalibrationDto buildCertificatoIn(Long calibrationId) {
        Calibration calib = calibrationRepo.findById(calibrationId)
                .orElseThrow(() -> new RuntimeException("Calibration not found: " + calibrationId));

        assertNotBlank(calib.getBaseInputJson(), "base_input_json");
        assertNotBlank(calib.getCalibrationMethodJson(), "calibration_method_json");
        assertNotBlank(calib.getMeasurestreamCompanyJson(), "measurestream_company_json");
        assertNotBlank(calib.getClientCompanyJson(), "client_company_json");
        assertNotBlank(calib.getJobJson(), "job_json");

        try {
            String result = pythonBridge.buildCertificatoIn(
                    calib.getBaseInputJson(),
                    calib.getCalibrationMethodJson(),
                    calib.getMeasurestreamCompanyJson(),
                    calib.getClientCompanyJson(),
                    calib.getJobJson()
            );
            calib.setCertificatoIn(result);
            calib.setProcessed(true);
            return toDto(calibrationRepo.save(calib));
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Python bridge error: " + e.getMessage(), e);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String loadBaseInput() {
        try {
            return new ClassPathResource("calibration_templates/base_input.json")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[Wizard] Could not load base_input.json: {}", e.getMessage());
            return "{}";
        }
    }

    private String loadCalibrationMethodTemplate() {
        try {
            return new ClassPathResource("calibration_templates/calibration_method.json")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[Wizard] Could not load calibration_method.json: {}", e.getMessage());
            return "{}";
        }
    }

    private String loadMeasurestreamCompanyTemplate() {
        try {
            return new ClassPathResource("calibration_templates/measurestream_company.json")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[Wizard] Could not load measurestream_company.json: {}", e.getMessage());
            return "{}";
        }
    }

    private String loadClientCompanyTemplate() {
        try {
            return new ClassPathResource("calibration_templates/client_company.json")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[Wizard] Could not load client_company.json: {}", e.getMessage());
            return "{}";
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String generateJobJson(CalibrationRequest req, Long sensorId, String modelName) {
        String today = LocalDate.now().format(DATE_FMT);
        String measurementDate = req.getCreatedAt() != null
                ? req.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                : today;
        String certId  = "MS-CAL-" + today.substring(0, 4) + "-" + String.format("%05d", req.getId());
        String certNum = today.substring(0, 4) + "/CAL/" + String.format("%05d", req.getId());

        String serialNum = sensorId != null ? sensorId.toString() : "";
        String model = modelName != null ? modelName : "";

        return "{\n" +
               "  \"calibration_specific_data\": {\n" +
               "    \"certificate_id\": \"" + certId + "\",\n" +
               "    \"certificate_number\": \"" + certNum + "\",\n" +
               "    \"asset_id\": \"MST-ASSET-" + String.format("%05d", sensorId != null ? sensorId : 0) + "\",\n" +
               "    \"lab_reference\": \"LAB-REF-" + today.substring(0, 4) + "-" + String.format("%04d", req.getId()) + "\",\n" +
               "    \"document_id\": \"" + certId + "\",\n" +
               "    \"issue_date\": \"" + today + "\",\n" +
               "    \"request_number\": \"RQ-" + today.substring(0, 4) + "-" + String.format("%04d", req.getId()) + "\",\n" +
               "    \"request_date\": \"" + measurementDate + "\",\n" +
               "    \"receipt_date\": \"" + measurementDate + "\",\n" +
               "    \"measurement_dates\": \"" + measurementDate + "\",\n" +
               "    \"environment\": {\n" +
               "      \"temperature\": \"(23.0 +/- 1.5) \\u00b0C\",\n" +
               "      \"relative_humidity\": \"(50 +/- 10) %\"\n" +
               "    }\n" +
               "  },\n" +
               "  \"sensor_method_template\": {\n" +
               "    \"model\": \"" + escapeJson(model) + "\",\n" +
               "    \"serial_number\": \"" + escapeJson(serialNum) + "\"\n" +
               "  },\n" +
               "  \"organization_data\": {\n" +
               "    \"authorised_by\": \"\",\n" +
               "    \"executed_by\": \"\",\n" +
               "    \"signature_name\": \"\"\n" +
               "  }\n" +
               "}\n";
    }

    private void assertNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank())
            throw new IllegalStateException("Campo mancante per il build: " + fieldName);
    }

    public CalibrationRepository calibrationRepo() { return calibrationRepo; }

    public CalibrationDto toDto(Calibration c) {
        CalibrationDto dto = new CalibrationDto();
        dto.setId(c.getId());
        dto.setMuId(c.getMuId());
        dto.setProcessed(c.isProcessed());
        dto.setCreatedAt(c.getCreatedAt());
        dto.setUpdatedAt(c.getUpdatedAt());
        dto.setSub(c.getSub());
        dto.setDescription(c.getDescription());
        dto.setCalibrationDeviceId(c.getCalibrationDeviceId());
        dto.setCalibrationRequestId(c.getCalibrationRequestId());
        dto.setBaseInputJson(c.getBaseInputJson());
        dto.setCalibrationMethodJson(c.getCalibrationMethodJson());
        dto.setMeasurestreamCompanyJson(c.getMeasurestreamCompanyJson());
        dto.setClientCompanyJson(c.getClientCompanyJson());
        dto.setJobJson(c.getJobJson());
        dto.setCertificatoIn(c.getCertificatoIn());
        dto.setResultJson(c.getResultJson());
        dto.setImages(c.getImages());
        dto.setRunId(c.getRunId());
        dto.setRunStatus(c.getRunStatus());
        dto.setRunLog(c.getRunLog());
        dto.setConformityJson(c.getConformityJson());
        dto.setPdfOutputUrl(c.getPdfOutputUrl());
        dto.setDccXml(c.getDccXml());
        return dto;
    }
}
