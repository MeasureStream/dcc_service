package de.ptb.dcc.controllers;

import de.ptb.dcc.dtos.*;
import de.ptb.dcc.entities.Dcc;
import de.ptb.dcc.entities.Sensor;
import de.ptb.dcc.entities.User;
import de.ptb.dcc.services.DccService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
public class DccController {

    private final DccService dccService;

    public DccController(DccService dccService) {
        this.dccService = dccService;
    }

    // -------------------------------------------------------------------------
    // DCCs
    // -------------------------------------------------------------------------

    @GetMapping("/api/dcc")
    public ResponseEntity<List<DccDto>> listDccs(
            @RequestParam(required = false) String sensorId,
            @RequestParam(required = false, defaultValue = "false") boolean template,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdTo,
            @RequestParam(defaultValue = "createdAt") String orderBy,
            @RequestParam(defaultValue = "desc") String orderDir,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        Page<Dcc> page = dccService.listDccs(sensorId, template, createdFrom, createdTo, orderBy, orderDir, limit, offset);
        List<DccDto> dtos = page.getContent().stream()
                .map(dccService::mapToDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/api/dcc")
    public ResponseEntity<DccDto> createDcc(@RequestBody DccCreateRequest request) {
        Dcc dcc = dccService.createDcc(request.getSensorId(), request.getName(), request.getDccJson());
        return ResponseEntity.status(HttpStatus.CREATED).body(dccService.mapToDto(dcc));
    }

    @GetMapping("/api/dcc/{dccId}")
    public ResponseEntity<DccDto> getDcc(@PathVariable Long dccId) {
        return dccService.getDcc(dccId)
                .map(dcc -> ResponseEntity.ok(dccService.mapToDto(dcc)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/api/dcc/{dccId}")
    public ResponseEntity<DccDto> updateDcc(@PathVariable Long dccId, @RequestBody DccUpdateRequest request) {
        return dccService.updateDcc(dccId, request)
                .map(dcc -> ResponseEntity.ok(dccService.mapToDto(dcc)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/dcc/{dccId}/validate")
    public ResponseEntity<DccDto> validateDcc(
            @PathVariable Long dccId,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "fileType", required = false) String fileType) {

        System.out.println("Validate request for DCC ID: " + dccId + ", fileType: " + fileType);

        if (fileType == null || fileType.isEmpty()) {
            return ResponseEntity.badRequest().body(null);
        }

        Dcc dcc = dccService.validateDcc(dccId, fileType);
        return ResponseEntity.ok(dccService.mapToDto(dcc));
    }

    @PostMapping("/api/dcc/{dccId}/json")
    public ResponseEntity<DccDto> updateDccJson(@PathVariable Long dccId, @RequestBody String dccJson) {
        Dcc dcc = dccService.updateDccJson(dccId, dccJson);
        return ResponseEntity.ok(dccService.mapToDto(dcc));
    }

    @PostMapping("/api/dcc/{dccId}/publish")
    public ResponseEntity<DccDto> publishDcc(@PathVariable Long dccId) {
        Dcc dcc = dccService.publishDcc(dccId);
        return ResponseEntity.ok(dccService.mapToDto(dcc));
    }

    @PostMapping("/api/dcc/{dccId}/unpublish")
    public ResponseEntity<DccDto> unpublishDcc(@PathVariable Long dccId) {
        Dcc dcc = dccService.unpublishDcc(dccId);
        return ResponseEntity.ok(dccService.mapToDto(dcc));
    }

    @DeleteMapping("/api/dcc/{dccId}")
    public ResponseEntity<Void> deleteDcc(@PathVariable Long dccId) {
        dccService.deleteDcc(dccId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/dcc/{dccId}/download/signed-xml")
    public ResponseEntity<byte[]> downloadSignedXml(@PathVariable Long dccId) throws Exception {
        byte[] content = dccService.getSignedXml(dccId);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"dcc-" + dccId + "-signed.xml\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_XML)
                .body(content);
    }

    @GetMapping("/api/dcc/{dccId}/download/signed-pdf")
    public ResponseEntity<byte[]> downloadSignedPdf(@PathVariable Long dccId) throws Exception {
        byte[] content = dccService.getSignedPdf(dccId);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"dcc-" + dccId + "-signed.pdf\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(content);
    }

    @GetMapping("/api/dcc/{dccId}/download")
    public ResponseEntity<byte[]> downloadDcc(@PathVariable Long dccId,
            @RequestParam(defaultValue = "PDF") String fileType) {
        byte[] content = "Mock DCC Content".getBytes();
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=\"dcc-" + dccId + "." + fileType.toLowerCase() + "\"")
                .body(content);
    }

    @GetMapping("/api/dcc/s3/{dccId}/{type}")
    public ResponseEntity<byte[]> downloadFromS3(@PathVariable Long dccId, @PathVariable String type) {
        byte[] content = dccService.downloadS3File(dccId, type);
        if (content == null) return ResponseEntity.notFound().build();

        String filename = "dcc-" + dccId + "." + type.toLowerCase();
        org.springframework.http.MediaType contentType = type.equalsIgnoreCase("xml")
                ? org.springframework.http.MediaType.APPLICATION_XML
                : org.springframework.http.MediaType.APPLICATION_PDF;

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .contentType(contentType)
                .body(content);
    }

    // -------------------------------------------------------------------------
    // SENSORS — lista sensori disponibili per linkare un DCC
    // admin: tutti, utente normale: solo quelli delle proprie CU
    // -------------------------------------------------------------------------

    @GetMapping("/api/sensors")
    public ResponseEntity<List<SensorDto>> listSensors() {
        List<SensorDto> dtos = dccService.listSensors().stream()
                .map(this::mapToSensorDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/api/public/sensors")
    public ResponseEntity<List<SensorDto>> listPublicSensors() {
        List<SensorDto> dtos = dccService.listPublicSensors().stream()
                .map(this::mapToSensorDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/api/public/dcc/{sensorId}")
    public ResponseEntity<DccDto> getPublicDcc(@PathVariable Long sensorId) {
        return dccService.getPublishedDccBySensorId(sensorId)
                .map(dcc -> ResponseEntity.ok(dccService.mapToDto(dcc)))
                .orElse(ResponseEntity.notFound().build());
    }

    // -------------------------------------------------------------------------
    // EXTERNAL VALIDATION
    // -------------------------------------------------------------------------

    @PostMapping("/api/dcc/external/validate-xml")
    public ResponseEntity<DccValidationResultDto> dccExternalValidateXml(
            @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        System.out.println("[INFO] External XML validation: " + (file != null ? file.getOriginalFilename() : "null"));
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(dccService.validateExternalXml(file));
    }

    @PostMapping("/api/dcc/external/validate-pdf")
    public ResponseEntity<DccValidationResultDto> dccExternalValidatePdf(
            @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        System.out.println("[INFO] External PDF validation: " + (file != null ? file.getOriginalFilename() : "null"));
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(dccService.validateExternalPdf(file));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<String> handleMissingFilePart(MissingServletRequestPartException ex) {
        System.err.println("[WARN] Missing multipart part: " + ex.getRequestPartName());
        return ResponseEntity.badRequest().body("Missing multipart part: " + ex.getRequestPartName());
    }

    // -------------------------------------------------------------------------
    // MISC
    // -------------------------------------------------------------------------

    @GetMapping("/verify-token")
    public ResponseEntity<String> verifyToken() {
        return ResponseEntity.ok("Token is valid");
    }

    // -------------------------------------------------------------------------
    // MAPPING HELPERS
    // -------------------------------------------------------------------------

    private SensorDto mapToSensorDto(Sensor sensor) {
        SensorDto dto = new SensorDto();
        dto.setId(sensor.getId());
        dto.setModelName(sensor.getModelName());
        dto.setSensorIndex(sensor.getSensorIndex());
        if (sensor.getMeasurementUnit() != null) {
            dto.setMuExtendedId(sensor.getMeasurementUnit().getExtendedId());
            if (sensor.getMeasurementUnit().getControlUnit() != null) {
                dto.setCuDevEui(sensor.getMeasurementUnit().getControlUnit().getDevEui());
            }
        }
        User owner = sensor.getOwner();
        if (owner != null) {
            dto.setOwnerId(owner.getUserId());
        }
        return dto;
    }

    private String calculateStatus(Dcc dcc) {
        if (!dcc.isPdfValid() || !dcc.isXmlValid()) return "RED";
        if (dcc.getExpirationDate() != null && dcc.getExpirationDate().isBefore(OffsetDateTime.now())) return "YELLOW";
        if (dcc.getPublishedAt() != null) return "BLUE";
        return "GREEN";
    }
}
