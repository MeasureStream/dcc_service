package de.ptb.dcc.controllers;

import de.ptb.dcc.dtos.*;
import de.ptb.dcc.entities.Calibration;
import de.ptb.dcc.entities.Dcc;
import de.ptb.dcc.entities.Sensor;
import de.ptb.dcc.entities.User;
import de.ptb.dcc.repositories.CalibrationRepository;
import de.ptb.dcc.services.CalibrationRunService;
import de.ptb.dcc.services.DccService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
public class DccController {

    private static final Logger log = LoggerFactory.getLogger(DccController.class);

    private final DccService dccService;
    private final CalibrationRunService calibrationRunService;
    private final CalibrationRepository calibrationRepository;

    @Value("${python.cmd:python}")
    private String pythonCmd;

    @Value("${calibration.script.path:}")
    private String calibrationScriptPath;

    @Value("${calibration.models.path:}")
    private String calibrationModelsPath;

    @Value("${calibration.runs.path:./calibration-runs}")
    private String calibrationRunsPath;

    public DccController(DccService dccService, CalibrationRunService calibrationRunService,
                         CalibrationRepository calibrationRepository) {
        this.dccService = dccService;
        this.calibrationRunService = calibrationRunService;
        this.calibrationRepository = calibrationRepository;
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
        Dcc dcc = dccService.createDcc(
                request.getSensorId(),
                request.getName(),
                request.getDccJson(),
                request.getCalibrationRequestId()
        );
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

    @GetMapping("/api/dcc/{dccId}/download/calibration-result")
    public ResponseEntity<byte[]> downloadCalibrationResult(@PathVariable Long dccId) {
        try {
            Dcc dcc = dccService.getDcc(dccId)
                    .orElseThrow(() -> new RuntimeException("DCC not found"));

            if (dcc.getCalibrationRequestId() == null) {
                return ResponseEntity.notFound().build();
            }

            Calibration calib = calibrationRepository.findByCalibrationRequestId(dcc.getCalibrationRequestId())
                    .orElseThrow(() -> new RuntimeException("Calibration not found for this DCC"));

            if (calib.getRunId() == null) {
                return ResponseEntity.notFound().build();
            }

            Path runsBase = Path.of(calibrationRunsPath).toAbsolutePath().normalize();
            Path pdfPath = runsBase.resolve(calib.getRunId()).resolve("output").resolve("ntc_cert_funzione.pdf");

            if (!Files.exists(pdfPath)) {
                return ResponseEntity.notFound().build();
            }

            byte[] content = Files.readAllBytes(pdfPath);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"calibration-result-dcc-" + dccId + ".pdf\"")
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .body(content);
        } catch (Exception e) {
            log.error("[DccController] downloadCalibrationResult error: {}", e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
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

    /**
     * POST /api/dcc/external/verify-conformity
     *
     * Accepts a DCC XML file upload + form parameters (sensor template name, mae,
     * pfa-threshold, u-ref).  Runs verify_dcc_conformity.py in a temp directory,
     * captures stdout and any generated PNG charts, and returns everything in the
     * response body (no DB persistence, temp dir is deleted after the call).
     */
    @PostMapping("/api/dcc/external/verify-conformity")
    public ResponseEntity<ConformityVerificationResultDto> verifyConformity(
            @RequestParam(value = "file")                    MultipartFile xmlFile,
            @RequestParam(value = "sensor", defaultValue = "ntc_temperature.json") String sensorFilename,
            @RequestParam(value = "mae",          defaultValue = "0.10")  double mae,
            @RequestParam(value = "pfaThreshold", defaultValue = "20.0")  double pfaThreshold,
            @RequestParam(value = "uRef",         defaultValue = "0.065") double uRef
    ) {
        if (xmlFile == null || xmlFile.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        Path tmpDir = null;
        try {
            // 1. Create temp directory
            tmpDir = Files.createTempDirectory("dcc_verify_conformity_");

            // 2. Write uploaded XML to temp dir
            Path xmlPath = tmpDir.resolve("uploaded_certificate.xml");
            xmlFile.transferTo(xmlPath.toFile());

            // 3. Resolve verify script path
            Path scriptPath = resolveVerifyScript();

            // 4. Resolve sensor model path
            Path sensorPath = resolveModelsDir().resolve("sensors").resolve(sensorFilename);

            // 5. Create images output dir
            Path imagesDir = tmpDir.resolve("images");
            Files.createDirectories(imagesDir);

            // 6. Build command
            List<String> cmd = new ArrayList<>();
            cmd.add(pythonCmd);
            cmd.add(scriptPath.toString());
            cmd.add("--xml");           cmd.add(xmlPath.toString());
            cmd.add("--sensor");        cmd.add(sensorPath.toString());
            cmd.add("--mae");           cmd.add(String.valueOf(mae));
            cmd.add("--pfa-threshold"); cmd.add(String.valueOf(pfaThreshold));
            cmd.add("--u-ref");         cmd.add(String.valueOf(uRef));
            cmd.add("--images-dir");    cmd.add(imagesDir.toString());

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.directory(scriptPath.getParent().toFile());

            log.info("[DccController] verifyConformity cmd: {}", cmd);

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.MINUTES);
            int exitCode = finished ? process.exitValue() : -1;
            if (!finished) {
                process.destroyForcibly();
                output += "\n[TIMEOUT] Process killed after 5 minutes.";
            }

            log.info("[DccController] verifyConformity exit={}", exitCode);

            // 7. Determine overall verdict from log
            String overall = "ERROR";
            if (exitCode == 0) {
                if (output.contains("OVERALL VERDICT: NON-CONFORMING")
                        || output.contains("OVERALL VERDICT: NON CONFORME")) {
                    overall = "NON-CONFORMING";
                } else if (output.contains("OVERALL VERDICT: CONFORMING")
                        || output.contains("OVERALL VERDICT: CONFORME")) {
                    overall = "CONFORMING";
                } else {
                    overall = "UNKNOWN";
                }
            }

            // 8. Collect generated images as Base64 data URIs
            List<ConformityVerificationResultDto.ConformityImageDto> images = new ArrayList<>();
            if (Files.isDirectory(imagesDir)) {
                try (var stream = Files.list(imagesDir)) {
                    stream.filter(p -> p.toString().endsWith(".png"))
                          .sorted()
                          .forEach(p -> {
                              try {
                                  byte[] bytes = Files.readAllBytes(p);
                                  String b64 = Base64.getEncoder().encodeToString(bytes);
                                  images.add(new ConformityVerificationResultDto.ConformityImageDto(
                                          p.getFileName().toString(),
                                          "data:image/png;base64," + b64
                                  ));
                              } catch (IOException e) {
                                  log.warn("[DccController] Could not read image {}: {}", p, e.getMessage());
                              }
                          });
                }
            }

            ConformityVerificationResultDto result = new ConformityVerificationResultDto(
                    exitCode == 0, overall, output, images
            );
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[DccController] verifyConformity error: {}", e.getMessage(), e);
            ConformityVerificationResultDto err = new ConformityVerificationResultDto(
                    false, "ERROR", "Internal error: " + e.getMessage(), List.of()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        } finally {
            // 9. Clean up temp directory
            if (tmpDir != null) {
                try {
                    Files.walk(tmpDir)
                         .sorted(Comparator.reverseOrder())
                         .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                } catch (IOException ignored) {}
            }
        }
    }

    /** Resolves verify_dcc_conformity.py path (adjacent to analisi_calib_data.py). */
    private Path resolveVerifyScript() {
        if (calibrationScriptPath != null && !calibrationScriptPath.isBlank()) {
            Path sibling = Path.of(calibrationScriptPath).resolveSibling("verify_dcc_conformity.py");
            if (Files.exists(sibling)) return sibling.toAbsolutePath();
        }
        Path candidate = Path.of("../calibration/scripts/verify_dcc_conformity.py").toAbsolutePath().normalize();
        if (Files.exists(candidate)) return candidate;
        throw new IllegalStateException(
                "verify_dcc_conformity.py not found. Set CALIBRATION_SCRIPT_PATH env var or place it at " + candidate);
    }

    /** Resolves the models_in directory (reuses same logic as CalibrationRunService). */
    private Path resolveModelsDir() {
        if (calibrationModelsPath != null && !calibrationModelsPath.isBlank()) {
            return Path.of(calibrationModelsPath).toAbsolutePath();
        }
        Path candidate = Path.of("../calibration/models_in").toAbsolutePath().normalize();
        if (Files.exists(candidate)) return candidate;
        throw new IllegalStateException(
                "models_in directory not found. Set CALIBRATION_MODELS_PATH env var or place it at " + candidate);
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
