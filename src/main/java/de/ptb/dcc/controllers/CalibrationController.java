package de.ptb.dcc.controllers;

import de.ptb.dcc.dtos.CalibrationMessageDto;
import de.ptb.dcc.dtos.CalibrationMessageLiteDto;
import de.ptb.dcc.dtos.CalibrationRequestDto;
import de.ptb.dcc.dtos.PagedResponse;
import de.ptb.dcc.entities.CalibrationMessage;
import de.ptb.dcc.entities.CalibrationRequest;
import de.ptb.dcc.entities.Calibration;
import de.ptb.dcc.repositories.CalibrationMessageRepository;
import de.ptb.dcc.repositories.CalibrationRepository;
import de.ptb.dcc.repositories.CalibrationRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/calibrations")
public class CalibrationController {

    private static final Logger log = LoggerFactory.getLogger(CalibrationController.class);

    private final CalibrationRequestRepository reqRepo;
    private final CalibrationMessageRepository msgRepo;
    private final CalibrationRepository calibrationRepo;

    public CalibrationController(CalibrationRequestRepository reqRepo,
                                  CalibrationMessageRepository msgRepo,
                                  CalibrationRepository calibrationRepo) {
        this.reqRepo = reqRepo;
        this.msgRepo = msgRepo;
        this.calibrationRepo = calibrationRepo;
    }

    // ── Calibration Requests ──────────────────────────────────────────────────

    /**
     * GET /api/calibrations/requests
     * Lista tutte le calibration requests, con filtri opzionali per sensorId e muId.
     * Le filtered branches non sono paginate (accettabile — il payload è leggero).
     */
    @GetMapping("/requests")
    public ResponseEntity<List<CalibrationRequestDto>> listRequests(
            @RequestParam(required = false) Long sensorId,
            @RequestParam(required = false) Long muId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        List<CalibrationRequest> requests;

        if (sensorId != null) {
            requests = reqRepo.findAllBySensorIdLight(sensorId);
        } else if (muId != null) {
            requests = reqRepo.findAllByMuIdLight(muId);
        } else {
            Page<CalibrationRequest> paged = reqRepo.findAllLight(
                    PageRequest.of(page, size));
            requests = paged.getContent();
        }

        List<CalibrationRequestDto> dtos = requests.stream()
                .map(this::toRequestDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * GET /api/calibrations/requests/{id}
     * Returns the full record including inputJson and processedJson.
     */
    @GetMapping("/requests/{id}")
    public ResponseEntity<CalibrationRequestDto> getRequest(@PathVariable Long id) {
        return reqRepo.findById(id)
                .map(r -> ResponseEntity.ok(toRequestDtoFull(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/calibrations/requests/{requestId}
     * Deletes the CalibrationRequest, its associated Calibration (wizard + run data),
     * and all related CalibrationMessages.
     */
    @DeleteMapping("/requests/{requestId}")
    public ResponseEntity<?> deleteRequest(@PathVariable Long requestId) {
        try {
            CalibrationRequest req = reqRepo.findById(requestId)
                    .orElseThrow(() -> new RuntimeException("CalibrationRequest not found: " + requestId));

            calibrationRepo.findByCalibrationRequestId(requestId).ifPresent(calib -> {
                if (calib.getRunId() != null) {
                    msgRepo.findByCalibIdOrderByStepIndexAsc(calib.getRunId())
                            .forEach(msgRepo::delete);
                }
                calibrationRepo.delete(calib);
                log.info("[Calibration] Deleted Calibration id={} for requestId={}", calib.getId(), requestId);
            });

            reqRepo.delete(req);
            log.info("[Calibration] Deleted CalibrationRequest id={} (calibrationId={})", requestId, req.getCalibrationId());
            return ResponseEntity.noContent().build();

        } catch (RuntimeException e) {
            log.error("[Calibration] deleteRequest failed for requestId={}: {}", requestId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    // ── Calibration Messages ──────────────────────────────────────────────────

    /**
     * GET /api/calibrations/messages
     * Lista messaggi in forma light (senza rawJson).
     * Paginazione: page/size oppure keyset via `before` (ISO timestamp cursore).
     * Filtro opzionale per calibId.
     */
    @GetMapping("/messages")
    public ResponseEntity<PagedResponse<CalibrationMessageLiteDto>> listMessages(
            @RequestParam(required = false) String calibId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size,
            @RequestParam(required = false) OffsetDateTime before) {

        if (calibId != null && before != null) {
            // keyset per singolo calib: usa stepIndex come cursore
            // (before è il timestamp ricevuto della riga più vecchia caricata)
            int afterStep = -1;
            // Recupera lo stepIndex della riga cursore
            List<CalibrationMessageLiteDto> lastPage = msgRepo.findByCalibIdLightPaged(calibId,
                    PageRequest.of(0, 1)).getContent();
            if (!lastPage.isEmpty()) {
                afterStep = lastPage.get(0).getStepIndex();
            }
            // Carica i successivi a partire da afterStep+1, ordinati ASC
            List<CalibrationMessageLiteDto> next = msgRepo.findByCalibIdAfterStepLight(calibId,
                    afterStep, PageRequest.of(0, size));
            long total = msgRepo.countByCalibId(calibId);
            boolean hasMore = (long) next.size() < total - (afterStep + 1);
            return ResponseEntity.ok(PagedResponse.of(next, page, size, total));
        }

        if (before != null) {
            // keyset globale: ricevuti più vecchi del cursore
            List<CalibrationMessageLiteDto> next = msgRepo.findAllBeforeLight(before, PageRequest.of(0, size));
            long total = msgRepo.count();
            boolean hasMore = next.size() == size;
            return ResponseEntity.ok(new PagedResponse<>(next, page, size, hasMore, total));
        }

        // Paginazione standard
        Page<CalibrationMessageLiteDto> paged = (calibId != null)
                ? msgRepo.findByCalibIdLightPaged(calibId, PageRequest.of(page, size))
                : msgRepo.findAllLight(PageRequest.of(page, size));

        return ResponseEntity.ok(PagedResponse.from(paged, m -> m));
    }

    /**
     * GET /api/calibrations/messages/count
     * Ritorna il numero totale di messaggi (per il badge nell'accordion prima dell'apertura).
     */
    @GetMapping("/messages/count")
    public ResponseEntity<Map<String, Long>> countMessages() {
        return ResponseEntity.ok(Map.of("total", msgRepo.count()));
    }

    /**
     * GET /api/calibrations/messages/{id}
     * Ritorna il singolo messaggio con il rawJson (per il preview on-demand).
     */
    @GetMapping("/messages/{id}")
    public ResponseEntity<CalibrationMessageDto> getMessage(@PathVariable Long id) {
        return msgRepo.findById(id)
                .map(m -> ResponseEntity.ok(toMessageDto(m)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    /** Light mapper for list — excludes large TEXT fields */
    private CalibrationRequestDto toRequestDto(CalibrationRequest r) {
        CalibrationRequestDto dto = new CalibrationRequestDto();
        dto.setId(r.getId());
        dto.setCalibrationId(r.getCalibrationId());
        dto.setCalibratorId(r.getCalibratorId());
        dto.setMuId(r.getMuId());
        dto.setSensorId(r.getSensorId());
        dto.setProcessed(r.isProcessed());
        dto.setCreatedAt(r.getCreatedAt());
        return dto;
    }

    /** Full mapper for single-record detail — includes inputJson and processedJson */
    private CalibrationRequestDto toRequestDtoFull(CalibrationRequest r) {
        CalibrationRequestDto dto = toRequestDto(r);
        dto.setInputJson(r.getInputJson());
        dto.setProcessedJson(r.getProcessedJson());
        return dto;
    }

    private CalibrationMessageDto toMessageDto(CalibrationMessage m) {
        CalibrationMessageDto dto = new CalibrationMessageDto();
        dto.setId(m.getId());
        dto.setCalibId(m.getCalibId());
        dto.setStepIndex(m.getStepIndex());
        dto.setTarget(m.getTarget());
        dto.setTotalSteps(m.getTotalSteps());
        dto.setAssembled(m.isAssembled());
        dto.setReceivedAt(m.getReceivedAt());
        dto.setRawJson(m.getRawJson());
        return dto;
    }
}
