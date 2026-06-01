package de.ptb.dcc.controllers;

import de.ptb.dcc.dtos.CalibrationDto;
import de.ptb.dcc.dtos.CalibrationRunConfig;
import de.ptb.dcc.dtos.CalibrationRunConfigOptions;
import de.ptb.dcc.dtos.DccDto;
import de.ptb.dcc.dtos.WizardStepRequest;
import de.ptb.dcc.entities.Calibration;
import de.ptb.dcc.entities.CalibrationRequest;
import de.ptb.dcc.repositories.CalibrationRequestRepository;
import de.ptb.dcc.services.CalibrationRunService;
import de.ptb.dcc.services.CalibrationWizardService;
import de.ptb.dcc.services.DccService;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Value("${calibration.runs.path:./calibration-runs}")
    private String calibrationRunsPath;

    @Value("${gemimeg.backend.url:http://gemimeg-backend:10001}")
    private String gemimegUrl;

    public CalibrationWizardController(CalibrationWizardService wizardService,
                                        CalibrationRunService runService,
                                        DccService dccService,
                                        CalibrationRequestRepository calibrationRequestRepository) {
        this.wizardService = wizardService;
        this.runService = runService;
        this.dccService = dccService;
        this.calibrationRequestRepository = calibrationRequestRepository;
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
     * GET /api/calibrations/static/runs/**
     * Serves static files from the calibration-runs directory (images, PDFs, XMLs).
     * Path format: /api/calibrations/static/runs/{runId}/{subpath}
     */
    @GetMapping("/static/runs/**")
    public ResponseEntity<Resource> serveStaticFile(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        // Extract everything after /static/runs/
        int idx = requestUri.indexOf("/static/runs/");
        if (idx < 0) return ResponseEntity.notFound().build();
        String relativePath = requestUri.substring(idx + "/static/runs/".length());

        Path runsBase = Path.of(calibrationRunsPath).toAbsolutePath().normalize();
        Path filePath = runsBase.resolve(relativePath).normalize();

        // Security: ensure the resolved path is within runs base
        if (!filePath.startsWith(runsBase)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(filePath);
        MediaType mediaType = detectMediaType(filePath.getFileName().toString());
        String filename = filePath.getFileName().toString();

        // Set Content-Disposition with the real filename so the browser
        // shows the correct name in the download dialog / PDF viewer tab.
        // Use "inline" so PDFs and images open in the browser tab rather than
        // triggering a forced download.
        org.springframework.http.ContentDisposition disposition =
                org.springframework.http.ContentDisposition.inline()
                        .filename(filename, java.nio.charset.StandardCharsets.UTF_8)
                        .build();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    private MediaType detectMediaType(String filename) {
        if (filename.endsWith(".png"))  return MediaType.IMAGE_PNG;
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (filename.endsWith(".pdf"))  return MediaType.APPLICATION_PDF;
        if (filename.endsWith(".xml"))  return MediaType.APPLICATION_XML;
        if (filename.endsWith(".json")) return MediaType.APPLICATION_JSON;
        return MediaType.APPLICATION_OCTET_STREAM;
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
