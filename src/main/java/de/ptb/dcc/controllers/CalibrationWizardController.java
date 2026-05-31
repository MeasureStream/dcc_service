package de.ptb.dcc.controllers;

import de.ptb.dcc.dtos.CalibrationDto;
import de.ptb.dcc.dtos.CalibrationRunConfig;
import de.ptb.dcc.dtos.CalibrationRunConfigOptions;
import de.ptb.dcc.dtos.WizardStepRequest;
import de.ptb.dcc.entities.Calibration;
import de.ptb.dcc.services.CalibrationRunService;
import de.ptb.dcc.services.CalibrationWizardService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

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

    @Value("${calibration.runs.path:./calibration-runs}")
    private String calibrationRunsPath;

    @Value("${gemimeg.backend.url:http://gemimeg-backend:8080}")
    private String gemimegUrl;

    public CalibrationWizardController(CalibrationWizardService wizardService,
                                        CalibrationRunService runService) {
        this.wizardService = wizardService;
        this.runService = runService;
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
     * POST /api/calibrations/wizard/{calibrationId}/dcc-json
     * Converts the DCC XML stored in this calibration to JSON via the gemimeg backend.
     * Used by the frontend "Salva DCC" flow (XML → JSON → POST /api/dcc).
     */
    @PostMapping("/wizard/{calibrationId}/dcc-json")
    public ResponseEntity<?> convertDccXmlToJson(@PathVariable Long calibrationId) {
        try {
            Calibration calib = wizardService.calibrationRepo().findById(calibrationId)
                    .orElseThrow(() -> new RuntimeException("Calibration not found: " + calibrationId));

            if (calib.getDccXml() == null || calib.getDccXml().isBlank()) {
                return ResponseEntity.badRequest()
                        .body("{\"error\": \"No DCC XML found for this calibration. Run the calibration first.\"}");
            }

            // Call gemimeg POST /api/v1/dcc/xsd/dcc/json (consumes XML, produces JSON)
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            HttpEntity<String> entity = new HttpEntity<>(calib.getDccXml(), headers);

            ResponseEntity<String> gemimegResponse = restTemplate.exchange(
                    gemimegUrl + "/api/v1/dcc/xsd/dcc/json",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(gemimegResponse.getBody());

        } catch (Exception e) {
            log.error("[Wizard] dcc-json conversion failed for calibrationId={}: {}", calibrationId, e.getMessage(), e);
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
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }

    private MediaType detectMediaType(String filename) {
        if (filename.endsWith(".png"))  return MediaType.IMAGE_PNG;
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (filename.endsWith(".pdf"))  return MediaType.APPLICATION_PDF;
        if (filename.endsWith(".xml"))  return MediaType.APPLICATION_XML;
        if (filename.endsWith(".json")) return MediaType.APPLICATION_JSON;
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
