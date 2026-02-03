package de.ptb.dcc.controllers;

import de.ptb.dcc.dtos.*;
import de.ptb.dcc.entities.Dcc;
import de.ptb.dcc.entities.MeasurementUnit;
import de.ptb.dcc.services.DccService;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@CrossOrigin(origins = "*")
public class DccController {

    private final DccService dccService;

    public DccController(DccService dccService) {
        this.dccService = dccService;
    }

    @GetMapping("/api/dcc")
    public ResponseEntity<List<DccDto>> listDccs(
            @RequestParam(required = false) String muId,
            @RequestParam(required = false, defaultValue = "false") boolean template,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdTo,
            @RequestParam(defaultValue = "createdAt") String orderBy,
            @RequestParam(defaultValue = "desc") String orderDir,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        Page<Dcc> page = dccService.listDccs(muId, template, createdFrom, createdTo, orderBy, orderDir, limit, offset);
        List<DccDto> dtos = page.getContent().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/api/mus")
    public ResponseEntity<List<MeasurementUnitDto>> listMus(
            @RequestParam(required = false, defaultValue = "false") boolean all) {
        // For now mock userId or get from security context if implemented
        String userId = "test-user";
        List<MeasurementUnit> mus = dccService.listMus(userId, all);
        List<MeasurementUnitDto> dtos = mus.stream()
                .map(this::mapToMuDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/api/public/mus")
    public ResponseEntity<List<MeasurementUnitDto>> listPublicMus(
            @RequestParam(required = false, defaultValue = "false") boolean all) {
        String userId = "test-user"; // Mock userId
        List<MeasurementUnit> mus = dccService.listPublicMus(userId, all);
        List<MeasurementUnitDto> dtos = mus.stream()
                .map(this::mapToMuDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/api/public/dcc/{muId}")
    public ResponseEntity<DccDto> getPublicDcc(@PathVariable Long muId) {
        return dccService.getPublishedDccByMuId(muId)
                .map(dcc -> ResponseEntity.ok(mapToDto(dcc)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/dcc")
    public ResponseEntity<DccDto> createDcc(@RequestBody DccCreateRequest request) {
        String createdBy = "anonymous";
        Dcc dcc = dccService.createDcc(request.getMuId(), request.getName(), createdBy, request.getDccJson());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToDto(dcc));
    }

    @GetMapping("/api/dcc/{dccId}")
    public ResponseEntity<DccDto> getDcc(@PathVariable Long dccId) {
        return dccService.getDcc(dccId)
                .map(dcc -> ResponseEntity.ok(mapToDto(dcc)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/verify-token")
    public ResponseEntity<String> verifyToken() {
        return ResponseEntity.ok("Token is valid");
    }

    @PutMapping("/api/dcc/{dccId}")
    public ResponseEntity<DccDto> updateDcc(@PathVariable Long dccId, @RequestBody DccUpdateRequest request) {
        return dccService.updateDcc(dccId, request)
                .map(dcc -> ResponseEntity.ok(mapToDto(dcc)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/dcc/{dccId}/validate")
    public ResponseEntity<DccDto> validateDcc(
            @PathVariable Long dccId,
            @RequestParam(value = "fileType", required = false) String fileType,
            @RequestHeader(value = "X-FileType", required = false) String fileTypeHeader,
            HttpServletRequest request) {

        // Log ALL available info for debugging
        log.info("--- Validation Request Diagnostic ---");
        log.info("Request URI: {}", request.getRequestURI());
        log.info("Query String: {}", request.getQueryString());
        log.info("Content Type: {}", request.getContentType());
        log.info("DCC ID: {}", dccId);
        log.info("fileType Param: {}", fileType);
        log.info("X-FileType Header: {}", fileTypeHeader);
        
        String effectiveFileType = fileType != null ? fileType : fileTypeHeader;

        if (effectiveFileType == null) {
            log.error("Validation rejected: No fileType found in query params or headers");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        
        try {
            // Note: 'file' parameter is removed from method signature to avoid 400 errors 
            // on non-multipart requests. If needed, we can extract it manually from the request.
            Dcc dcc = dccService.validateDcc(dccId, effectiveFileType);
            return ResponseEntity.ok(mapToDto(dcc));
        } catch (Exception e) {
            log.error("Validation Service Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping("/api/dcc/external/validate-xml")
    public ResponseEntity<DccValidationResultDto> dccExternalValidateXml(
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(dccService.validateExternalXml(file));
    }

    @PostMapping("/api/dcc/external/validate-pdf")
    public ResponseEntity<DccValidationResultDto> dccExternalValidatePdf(
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(dccService.validateExternalPdf(file));
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

    @PostMapping("/api/dcc/{dccId}/json")
    public ResponseEntity<DccDto> updateDccJson(
            @PathVariable Long dccId,
            @RequestBody String dccJson) {
        Dcc dcc = dccService.updateDccJson(dccId, dccJson);
        return ResponseEntity.ok(mapToDto(dcc));
    }

    @PostMapping("/api/dcc/{dccId}/publish")
    public ResponseEntity<DccDto> publishDcc(@PathVariable Long dccId) {
        Dcc dcc = dccService.publishDcc(dccId);
        return ResponseEntity.ok(mapToDto(dcc));
    }

    @PostMapping("/api/dcc/{dccId}/unpublish")
    public ResponseEntity<DccDto> unpublishDcc(@PathVariable Long dccId) {
        Dcc dcc = dccService.unpublishDcc(dccId);
        return ResponseEntity.ok(mapToDto(dcc));
    }

    @DeleteMapping("/api/dcc/{dccId}")
    public ResponseEntity<Void> deleteDcc(@PathVariable Long dccId) {
        dccService.deleteDcc(dccId);
        return ResponseEntity.noContent().build();
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

    private MeasurementUnitDto mapToMuDto(MeasurementUnit mu) {
        MeasurementUnitDto dto = new MeasurementUnitDto();
        dto.setId(mu.getId());
        dto.setType(mu.getType());
        dto.setMeasuresUnit(mu.getMeasuresUnit());
        dto.setNetworkId(mu.getNetworkId());
        if (mu.getNode() != null) {
            dto.setNodeId(mu.getNode().getId());
        }
        if (mu.getUser() != null) {
            dto.setOwnerId(mu.getUser().getUserId());
        }
        return dto;
    }

    private DccDto mapToDto(Dcc dcc) {
        DccDto dto = new DccDto();
        dto.setId(dcc.getId());
        if (dcc.getMu() != null) {
            dto.setMuId(dcc.getMu().getId().toString());
        }
        dto.setName(dcc.getName());
        dto.setCreatedBy(dcc.getCreatedBy());
        dto.setCreatedAt(dcc.getCreatedAt());
        dto.setUpdatedAt(dcc.getUpdatedAt());
        dto.setPdfValid(dcc.isPdfValid());
        dto.setXmlValid(dcc.isXmlValid());
        dto.setPdfUrl(dcc.getPdfUrl());
        dto.setXmlUrl(dcc.getXmlUrl());
        dto.setDccJson(dcc.getDccJson());
        dto.setPublishedAt(dcc.getPublishedAt());
        dto.setCalibrationDate(dcc.getCalibrationDate());
        dto.setExpirationDate(dcc.getExpirationDate());
        dto.setHashXml(dcc.getHashXml());
        dto.setHashPdf(dcc.getHashPdf());
        dto.setStatus(calculateStatus(dcc));
        return dto;
    }

    private String calculateStatus(Dcc dcc) {
        if (!dcc.isPdfValid() || !dcc.isXmlValid()) {
            return "RED";
        }
        if (dcc.getPublishedAt() == null) {
            return "YELLOW";
        }
        return "GREEN";
    }
}
