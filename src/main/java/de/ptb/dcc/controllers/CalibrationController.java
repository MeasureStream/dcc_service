package de.ptb.dcc.controllers;

import de.ptb.dcc.dtos.CalibrationMessageDto;
import de.ptb.dcc.dtos.CalibrationRequestDto;
import de.ptb.dcc.entities.CalibrationMessage;
import de.ptb.dcc.entities.CalibrationRequest;
import de.ptb.dcc.repositories.CalibrationMessageRepository;
import de.ptb.dcc.repositories.CalibrationRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/calibrations")
public class CalibrationController {

    private final CalibrationRequestRepository reqRepo;
    private final CalibrationMessageRepository msgRepo;

    public CalibrationController(CalibrationRequestRepository reqRepo,
                                  CalibrationMessageRepository msgRepo) {
        this.reqRepo = reqRepo;
        this.msgRepo = msgRepo;
    }

    // ── Calibration Requests ──────────────────────────────────────────────────

    /**
     * GET /api/calibrations/requests
     * Lista tutte le calibration requests, con filtri opzionali per sensorId e muId.
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

    // ── Calibration Messages ──────────────────────────────────────────────────

    /**
     * GET /api/calibrations/messages
     * Lista tutti i messaggi raw, opzionalmente filtrati per calib_id.
     */
    @GetMapping("/messages")
    public ResponseEntity<List<CalibrationMessageDto>> listMessages(
            @RequestParam(required = false) String calibId) {

        List<CalibrationMessage> msgs = calibId != null
                ? msgRepo.findByCalibIdOrderByStepIndexAsc(calibId)
                : msgRepo.findAll(Sort.by(Sort.Direction.DESC, "receivedAt"));

        List<CalibrationMessageDto> dtos = msgs.stream()
                .map(this::toMessageDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
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
