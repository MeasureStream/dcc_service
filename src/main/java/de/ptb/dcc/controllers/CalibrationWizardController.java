package de.ptb.dcc.controllers;

import de.ptb.dcc.dtos.CalibrationDto;
import de.ptb.dcc.dtos.CalibrationRunConfig;
import de.ptb.dcc.dtos.CalibrationRunConfigOptions;
import de.ptb.dcc.dtos.CalibrationStatusDto;
import de.ptb.dcc.dtos.DccDto;
import de.ptb.dcc.dtos.ManualCertificateRequest;
import de.ptb.dcc.dtos.WizardStepRequest;
import de.ptb.dcc.entities.Calibration;
import de.ptb.dcc.entities.CalibrationRequest;
import de.ptb.dcc.repositories.CalibrationRequestRepository;
import de.ptb.dcc.services.CalibrationRunService;
import de.ptb.dcc.services.CalibrationWizardService;
import de.ptb.dcc.services.DccService;
import de.ptb.dcc.services.S3Service;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Wizard endpoint per la compilazione certificato.
 *
 * POST /api/calibrations/requests/{requestId}/wizard/init
 *   → Inizializza o ricarica la Calibration associata alla CalibrationRequest.
 *     Risponde con il CalibrationDto (con i JSON già salvati se il wizard era stato aperto prima).
 *
 * GET  /api/calibrations/wizard/{calibrationId}
 *   → Legge la Calibration corrente (tutti gli step).
 *
 * PUT  /api/calibrations/wizard/{calibrationId}/step
 *   → Salva il JSON di uno step (body: WizardStepRequest {step, jsonData}).
 *
 * POST /api/calibrations/wizard/{calibrationId}/build
 *   → Lancia build_input_json.py e salva certificato_in.
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/calibrations")
public class CalibrationWizardController {

    private static final Logger log = LoggerFactory.getLogger(CalibrationWizardController.class);

    private final CalibrationWizardService wizardService;
    private final CalibrationRunService runService;
    private final DccService dccService;
    private final CalibrationRequestRepository calibrationRequestRepository;
    private final S3Service s3Service;

    private static final String S3_KEY_PREFIX = "calibration-runs/";

    @Value("${calibration.runs.path:./calibration-runs}")
    private String calibrationRunsPath;

    @Value("${gemimeg.backend.url:http://gemimeg-backend:10001}")
    private String gemimegUrl;

    public CalibrationWizardController(CalibrationWizardService wizardService,
                                        CalibrationRunService runService,
                                        DccService dccService,
                                        CalibrationRequestRepository calibrationRequestRepository,
                                        S3Service s3Service) {
        this.wizardService = wizardService;
        this.runService = runService;
        this.dccService = dccService;
        this.calibrationRequestRepository = calibrationRequestRepository;
        this.s3Service = s3Service;
    }

    /** Inizializza o ricarica il wizard per una CalibrationRequest */
    @PostMapping("/requests/{requestId}/wizard/init")
    public ResponseEntity<?> initWizard(
            @PathVariable Long requestId,
            @AuthenticationPrincipal Jwt jwt) {
        try {
            String sub = (jwt != null && jwt.getSubject() != null) ? jwt.getSubject() : "system";
            CalibrationDto dto = wizardService.initWizard(requestId, sub);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("[Wizard] initWizard failed for requestId={}: {}", requestId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    /**
     * POST /api/calibrations/manual/init
     *
     * Creates a new certificate from scratch (no prior CalibrationRequest from Kafka).
     * The backend creates an internal CalibrationRequest, initialises the wizard with
     * all five step templates, and returns the CalibrationDto so the frontend can open
     * the CertificatoWizard immediately.
     *
     * Body: { name (required), sensorId (optional), muId (optional) }
     */
    @PostMapping("/manual/init")
    public ResponseEntity<?> initManualWizard(
            @RequestBody ManualCertificateRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        try {
            if (req.getName() == null || req.getName().isBlank()) {
                return ResponseEntity.badRequest().body("{\"error\": \"name is required\"}");
            }

            String sub = (jwt != null && jwt.getSubject() != null) ? jwt.getSubject() : "system";

            // Build a unique calibration_id for the internal CalibrationRequest
            String calibId = "man-" + (req.getSensorId() != null ? req.getSensorId() : "0")
                    + "-" + (req.getMuId() != null ? req.getMuId() : "0")
                    + "-" + Instant.now().toEpochMilli();

            CalibrationRequest request = new CalibrationRequest();
            request.setCalibrationId(calibId);
            request.setCalibratorId(0L);
            request.setSensorId(req.getSensorId());
            request.setMuId(req.getMuId());
            request.setInputJson("{}");
            request.setProcessedJson("{}");
            request.setProcessed(false);

            CalibrationRequest saved = calibrationRequestRepository.save(request);
            log.info("[Wizard] Created manual CalibrationRequest: id={} calibId={}", saved.getId(), calibId);

            CalibrationDto dto = wizardService.initWizard(saved.getId(), sub);

            // Store the certificate name on the Calibration description field so
            // save-dcc-blank can retrieve it later
            wizardService.calibrationRepo().findById(dto.getId()).ifPresent(c -> {
                c.setDescription(req.getName());
                wizardService.calibrationRepo().save(c);
            });
            dto.setDescription(req.getName());

            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("[Wizard] manual/init failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    /** Legge lo stato corrente del wizard per calibrationId */
    @GetMapping("/wizard/{calibrationId}")
    public ResponseEntity<CalibrationDto> getWizard(@PathVariable Long calibrationId) {
        return wizardService.calibrationRepo().findById(calibrationId)
                .map(c -> ResponseEntity.ok(wizardService.toDto(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/calibrations/requests/{requestId}/calibration
     * Restituisce la Calibration associata a una CalibrationRequest (se esiste).
     * Usato dal frontend per sapere se il certificato_in è già stato generato.
     */
    @GetMapping("/requests/{requestId}/calibration")
    public ResponseEntity<CalibrationDto> getCalibrationByRequest(@PathVariable Long requestId) {
        return wizardService.calibrationRepo().findByCalibrationRequestId(requestId)
                .map(c -> ResponseEntity.ok(wizardService.toDto(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/calibrations/requests/{requestId}/calibration/status
     * Slim status (no wizard JSON fields). Usato dalla tabella per decidere quali bottoni mostrare.
     * 404 se non esiste ancora una Calibration per questa request (nessun bottone wizard/run/save).
     */
    @GetMapping("/requests/{requestId}/calibration/status")
    public ResponseEntity<CalibrationStatusDto> getCalibrationStatusByRequest(@PathVariable Long requestId) {
        return wizardService.calibrationRepo().findByCalibrationRequestId(requestId)
                .map(c -> ResponseEntity.ok(toStatusDto(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    private CalibrationStatusDto toStatusDto(Calibration c) {
        return new CalibrationStatusDto(
                c.getId(),
                c.getCertificatoIn() != null && !c.getCertificatoIn().isBlank(),
                c.getRunStatus(),
                c.getDccXml() != null && !c.getDccXml().isBlank(),
                c.getRunId()
        );
    }

    /** Salva il JSON di uno step (0-4) */
    @PutMapping("/wizard/{calibrationId}/step")
    public ResponseEntity<?> saveStep(
            @PathVariable Long calibrationId,
            @RequestBody WizardStepRequest req) {
        try {
            return ResponseEntity.ok(wizardService.saveStep(calibrationId, req));
        } catch (Exception e) {
            log.error("[Wizard] saveStep failed for calibrationId={} step={}: {}", calibrationId, req.getStep(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    /** Esegue build_input_json.py e salva certificato_in */
    @PostMapping("/wizard/{calibrationId}/build")
    public ResponseEntity<?> build(@PathVariable Long calibrationId) {
        try {
            return ResponseEntity.ok(wizardService.buildCertificatoIn(calibrationId));
        } catch (Exception e) {
            log.error("[Wizard] build failed for calibrationId={}: {}", calibrationId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    /**
     * GET /api/calibrations/wizard/{calibrationId}/run-config
     * Returns available sensor/reference templates and procedure options for the CalibrationRunModal.
     */
    @GetMapping("/wizard/{calibrationId}/run-config")
    public ResponseEntity<?> getRunConfig(@PathVariable Long calibrationId) {
        try {
            CalibrationRunConfigOptions opts = runService.getRunConfig(calibrationId);
            return ResponseEntity.ok(opts);
        } catch (Exception e) {
            log.error("[Wizard] getRunConfig failed for calibrationId={}: {}", calibrationId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    /**
     * POST /api/calibrations/wizard/{calibrationId}/run
     * Launches analisi_calib_data.py synchronously with the user-supplied configuration.
     * Blocks until the process completes (up to 10 min) and returns the updated CalibrationDto.
     */
    @PostMapping("/wizard/{calibrationId}/run")
    public ResponseEntity<?> runCalibration(
            @PathVariable Long calibrationId,
            @RequestBody CalibrationRunConfig config) {
        try {
            CalibrationDto result = runService.runCalibration(calibrationId, config);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            log.warn("[Wizard] runCalibration precondition failed for calibrationId={}: {}", calibrationId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body("{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}");
        } catch (Exception e) {
            log.error("[Wizard] runCalibration failed for calibrationId={}: {}", calibrationId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    /**
     * POST /api/calibrations/wizard/{calibrationId}/save-dcc
     *
     * All-in-one "Salva DCC" backend flow:
     *   1. Reads the DCC XML stored on this Calibration entity
     *   2. Converts it to JSON via gemimeg POST /api/v1/dcc/xsd/dcc/json (UTF-8 safe)
     *   3. Resolves sensorId from the linked CalibrationRequest
     *   4. Creates a DCC record with sensorId + calibrationRequestId both set
     *   5. Returns the saved DccDto
     *
     * The frontend only calls this one endpoint — no separate POST /api/dcc needed.
     */
    @PostMapping("/wizard/{calibrationId}/save-dcc")
    public ResponseEntity<?> saveDccFromCalibration(@PathVariable Long calibrationId) {
        try {
            Calibration calib = wizardService.calibrationRepo().findById(calibrationId)
                    .orElseThrow(() -> new RuntimeException("Calibration not found: " + calibrationId));

            if (calib.getDccXml() == null || calib.getDccXml().isBlank()) {
                return ResponseEntity.badRequest()
                        .body("{\"error\": \"No DCC XML found for this calibration. Run the calibration first.\"}");
            }

            // ── Step 1: Convert XML → JSON via gemimeg ─────────────────────
            // RestTemplate's StringHttpMessageConverter defaults to ISO-8859-1 for
            // application/xml, corrupting multi-byte UTF-8 chars (°, –, ≈).
            // Fix: force UTF-8 on the converter and in the Content-Type header.
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.getMessageConverters().stream()
                    .filter(c -> c instanceof StringHttpMessageConverter)
                    .map(c -> (StringHttpMessageConverter) c)
                    .forEach(c -> c.setDefaultCharset(StandardCharsets.UTF_8));

            String xmlPayload = sanitizeUtf8(calib.getDccXml());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("application", "xml", StandardCharsets.UTF_8));
            headers.setAcceptCharset(List.of(StandardCharsets.UTF_8));
            HttpEntity<String> entity = new HttpEntity<>(xmlPayload, headers);

            ResponseEntity<String> gemimegResponse = restTemplate.exchange(
                    gemimegUrl + "/api/v1/dcc/xsd/dcc/json",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            String dccJson = gemimegResponse.getBody();

            // ── Step 2: Resolve calibrationRequestId and sensorId ──────────
            Long calibrationRequestId = calib.getCalibrationRequestId();
            String sensorId = null;

            if (calibrationRequestId != null) {
                CalibrationRequest req = calibrationRequestRepository
                        .findById(calibrationRequestId).orElse(null);
                if (req != null && req.getSensorId() != null) {
                    sensorId = req.getSensorId().toString();
                }
            }

            // ── Step 3: Create DCC record with both IDs ────────────────────
            String dccName = "DCC — " + (calib.getRunId() != null ? calib.getRunId()
                    : "calibration-" + calibrationId);

            de.ptb.dcc.entities.Dcc savedDcc = dccService.createDcc(
                    sensorId,
                    dccName,
                    dccJson,
                    calibrationRequestId
            );

            DccDto dto = dccService.mapToDto(savedDcc);
            log.info("[Wizard] DCC saved: id={} sensorId={} calibrationRequestId={}",
                    dto.getId(), dto.getSensorId(), calibrationRequestId);
            return ResponseEntity.ok(dto);

        } catch (Exception e) {
            log.error("[Wizard] save-dcc failed for calibrationId={}: {}", calibrationId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    /**
     * POST /api/calibrations/wizard/{calibrationId}/save-dcc-blank
     *
     * Creates a DCC record directly from the certificato_in JSON (no calibration
     * run required).  If certificato_in has not been built yet the endpoint runs
     * the build step first.
     *
     * The certificato_in JSON is stored as the DCC's dccJson.  Later
     * sign/validate via gemimeg will convert it to XML/PDF on demand.
     */
    @PostMapping("/wizard/{calibrationId}/save-dcc-blank")
    public ResponseEntity<?> saveDccBlank(@PathVariable Long calibrationId) {
        try {
            Calibration calib = wizardService.calibrationRepo().findById(calibrationId)
                    .orElseThrow(() -> new RuntimeException("Calibration not found: " + calibrationId));

            // Auto-build certificato_in if not done yet
            if (calib.getCertificatoIn() == null || calib.getCertificatoIn().isBlank()) {
                log.info("[Wizard] save-dcc-blank: building certificato_in for calibrationId={}", calibrationId);
                CalibrationDto built = wizardService.buildCertificatoIn(calibrationId);
                calib = wizardService.calibrationRepo().findById(calibrationId)
                        .orElseThrow(() -> new RuntimeException("Calibration disappeared: " + calibrationId));
            }

            String dccJson = calib.getCertificatoIn();

            // Resolve sensorId from the CalibrationRequest
            Long calibrationRequestId = calib.getCalibrationRequestId();
            String sensorId = null;
            if (calibrationRequestId != null) {
                CalibrationRequest req = calibrationRequestRepository
                        .findById(calibrationRequestId).orElse(null);
                if (req != null && req.getSensorId() != null) {
                    sensorId = req.getSensorId().toString();
                }
            }

            // Use the description field as DCC name (set during manual/init)
            String dccName = calib.getDescription();
            if (dccName == null || dccName.isBlank()) {
                dccName = "DCC — calibration-" + calibrationId;
            }

            de.ptb.dcc.entities.Dcc savedDcc = dccService.createDcc(
                    sensorId,
                    dccName,
                    dccJson,
                    calibrationRequestId
            );

            DccDto dto = dccService.mapToDto(savedDcc);
            log.info("[Wizard] DCC blank-saved: id={} sensorId={} calibrationRequestId={}",
                    dto.getId(), dto.getSensorId(), calibrationRequestId);
            return ResponseEntity.ok(dto);

        } catch (Exception e) {
            log.error("[Wizard] save-dcc-blank failed for calibrationId={}: {}", calibrationId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    /**
     * GET /api/calibrations/sensor-templates
     * Returns the list of available sensor template JSON file names from models_in/sensors/.
     * Does not require a calibration ID — used by standalone verify-conformity UI.
     */
    @GetMapping("/sensor-templates")
    public ResponseEntity<?> getSensorTemplates() {
        try {
            java.util.List<String> sensors = runService.listSensorTemplates();
            return ResponseEntity.ok(sensors);
        } catch (Exception e) {
            log.error("[Wizard] getSensorTemplates failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    /**
     * GET /api/calibrations/s3/runs/**
     * Serves calibration run files from S3, with local filesystem fallback.
     * Path format: /api/calibrations/s3/runs/{runId}/{subpath}
     */
    @GetMapping("/s3/runs/**")
    public ResponseEntity<byte[]> serveS3File(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        int idx = requestUri.indexOf("/s3/runs/");
        if (idx < 0) return ResponseEntity.notFound().build();
        String relativePath = requestUri.substring(idx + "/s3/runs/".length());

        String s3Key = S3_KEY_PREFIX + relativePath.replace('\\', '/');

        // Try S3 first
        if (s3Service != null && s3Service.isAvailable()) {
            try {
                byte[] content = s3Service.downloadFile(s3Key);
                if (content != null && content.length > 0) {
                    MediaType mediaType = detectMediaType(extractFilename(relativePath));
                    String filename = extractFilename(relativePath);
                    org.springframework.http.ContentDisposition disposition =
                            org.springframework.http.ContentDisposition.inline()
                                    .filename(filename, java.nio.charset.StandardCharsets.UTF_8)
                                    .build();

                    return ResponseEntity.ok()
                            .contentType(mediaType)
                            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                            .body(content);
                }
            } catch (Exception e) {
                log.warn("[CalibrationWizard] S3 download failed for key={}, falling back to local: {}", s3Key, e.getMessage());
            }
        }

        // Fallback to local filesystem
        Path runsBase = Path.of(calibrationRunsPath).toAbsolutePath().normalize();
        Path filePath = runsBase.resolve(relativePath).normalize();

        if (!filePath.startsWith(runsBase)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] content = Files.readAllBytes(filePath);
            MediaType mediaType = detectMediaType(filePath.getFileName().toString());
            String filename = filePath.getFileName().toString();
            org.springframework.http.ContentDisposition disposition =
                    org.springframework.http.ContentDisposition.inline()
                            .filename(filename, java.nio.charset.StandardCharsets.UTF_8)
                            .build();

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(content);
        } catch (IOException e) {
            log.error("[CalibrationWizard] Local file read failed: {}", filePath, e);
            return ResponseEntity.notFound().build();
        }
    }

    private MediaType detectMediaType(String filename) {
        if (filename.endsWith(".png"))  return MediaType.IMAGE_PNG;
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (filename.endsWith(".pdf"))  return MediaType.APPLICATION_PDF;
        if (filename.endsWith(".xml"))  return MediaType.APPLICATION_XML;
        if (filename.endsWith(".json")) return MediaType.APPLICATION_JSON;
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private String extractFilename(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Sanitizes a string to ensure it contains only valid Unicode code points.
     *
     * When the DCC XML is stored as a Java String in PostgreSQL TEXT column and read back,
     * any stray bytes that slipped in via wrong encoding at write-time could cause
     * JAXB/gemimeg to reject the payload with "Invalid byte N of M-byte UTF-8 sequence".
     *
     * This method round-trips the string through UTF-8 bytes with REPLACE error handling,
     * substituting any un-encodable surrogate or private-use characters with the Unicode
     * replacement character (U+FFFD), then strips those replacement characters so the XML
     * stays well-formed.
     *
     * Valid multi-byte UTF-8 characters (°, –, ≈, etc.) pass through unchanged because
     * they are already valid Unicode code points in the Java String.
     */
    private static String sanitizeUtf8(String input) {
        if (input == null) return null;
        // Encode to UTF-8 bytes with replacement for unmappable chars, then decode back
        byte[] utf8Bytes = input.getBytes(StandardCharsets.UTF_8);
        String roundTripped = new String(utf8Bytes, StandardCharsets.UTF_8);
        // Remove any replacement characters introduced by the round-trip
        return roundTripped.replace("\uFFFD", "");
    }
}
