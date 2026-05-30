package de.ptb.dcc.controllers;

import de.ptb.dcc.dtos.CalibrationDto;
import de.ptb.dcc.dtos.WizardStepRequest;
import de.ptb.dcc.services.CalibrationWizardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

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

    public CalibrationWizardController(CalibrationWizardService wizardService) {
        this.wizardService = wizardService;
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
}
