package de.ptb.dcc.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.ptb.dcc.dtos.CalibrationDto;
import de.ptb.dcc.dtos.CalibrationRunConfig;
import de.ptb.dcc.dtos.CalibrationRunConfigOptions;
import de.ptb.dcc.entities.Calibration;
import de.ptb.dcc.entities.CalibrationRequest;
import de.ptb.dcc.entities.Sensor;
import de.ptb.dcc.repositories.CalibrationRepository;
import de.ptb.dcc.repositories.CalibrationRequestRepository;
import de.ptb.dcc.repositories.SensorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Orchestrates the full calibration run:
 * 1. Resolves paths (runs dir, script, models)
 * 2. Prepares the per-run directory structure
 * 3. Writes input files
 * 4. Invokes analisi_calib_data.py via PythonBridgeService
 * 5. Reads output files, builds image URL list
 * 6. Persists results back to the Calibration entity
 */
@Service
public class CalibrationRunService {

    private static final Logger log = LoggerFactory.getLogger(CalibrationRunService.class);

    private static final String STATIC_BASE = "/api/calibrations/static/runs/";
    private static final String S3_BASE     = "/api/calibrations/s3/runs/";
    private static final String S3_KEY_PREFIX = "calibration-runs/";

    @Value("${calibration.script.path:}")
    private String calibrationScriptPath;

    @Value("${calibration.models.path:}")
    private String calibrationModelsPath;

    @Value("${calibration.runs.path:./calibration-runs}")
    private String calibrationRunsPath;

    private final CalibrationRepository calibrationRepo;
    private final CalibrationRequestRepository requestRepo;
    private final SensorRepository sensorRepo;
    private final PythonBridgeService pythonBridge;
    private final CalibrationWizardService wizardService;
    private final SensorCoefficientUpdater sensorCoeffUpdater;
    private final S3Service s3Service;

    public CalibrationRunService(CalibrationRepository calibrationRepo,
                                  CalibrationRequestRepository requestRepo,
                                  SensorRepository sensorRepo,
                                  PythonBridgeService pythonBridge,
                                  CalibrationWizardService wizardService,
                                  SensorCoefficientUpdater sensorCoeffUpdater,
                                  S3Service s3Service) {
        this.calibrationRepo = calibrationRepo;
        this.requestRepo = requestRepo;
        this.sensorRepo = sensorRepo;
        this.pythonBridge = pythonBridge;
        this.wizardService = wizardService;
        this.sensorCoeffUpdater = sensorCoeffUpdater;
        this.s3Service = s3Service;
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Returns the available sensor/reference template names and the run ID for this calibration.
     */
    public CalibrationRunConfigOptions getRunConfig(Long calibrationId) {
        Calibration calib = calibrationRepo.findById(calibrationId)
                .orElseThrow(() -> new RuntimeException("Calibration not found: " + calibrationId));

        Path modelsDir = resolveModelsDir();
        List<String> sensors = listJsonFiles(modelsDir.resolve("sensors"));
        List<String> refs    = listJsonFiles(modelsDir.resolve("references"));

        // Derive run ID from the linked CalibrationRequest
        String runId = deriveRunId(calib);

        CalibrationRunConfigOptions opts = new CalibrationRunConfigOptions();
        opts.setAvailableSensors(sensors);
        opts.setAvailableRefs(refs);
        opts.setRunId(runId);
        opts.setHasExistingRun(calib.getRunStatus() != null);
        return opts;
    }

    /**
     * Launches analisi_calib_data.py synchronously.
     * Writes all outputs to runs/<runId>/, persists results in the Calibration entity.
     */
    public CalibrationDto runCalibration(Long calibrationId, CalibrationRunConfig config) {
        Calibration calib = calibrationRepo.findById(calibrationId)
                .orElseThrow(() -> new RuntimeException("Calibration not found: " + calibrationId));

        if (calib.getCertificatoIn() == null || calib.getCertificatoIn().isBlank()) {
            throw new IllegalStateException("certificato_in is not built yet. Run the wizard build step first.");
        }

        CalibrationRequest req = null;
        if (calib.getCalibrationRequestId() != null) {
            req = requestRepo.findById(calib.getCalibrationRequestId()).orElse(null);
        }
        if (req == null || req.getProcessedJson() == null || req.getProcessedJson().isBlank()) {
            throw new IllegalStateException("CalibrationRequest has no processedJson. Cannot run calibration.");
        }

        // Resolve Sensor from DB — source of truth for current calibration coefficients
        Sensor sensor = null;
        if (req.getSensorId() != null) {
            sensor = sensorRepo.findById(req.getSensorId()).orElse(null);
        }
        // Fallback: resolve by mu_id when sensor_id is missing or not found
        if (sensor == null && req.getMuId() != null && req.getMuId() > 0) {
            var sensors = sensorRepo.findAllByMeasurementUnit_Id(req.getMuId());
            if (!sensors.isEmpty()) {
                sensor = sensors.get(0);
                log.warn("[CalibRunService] sensor_id={} not found — resolved sensor via mu_id={} → sensor.id={}",
                        req.getSensorId(), req.getMuId(), sensor.getId());
            }
        }
        if (sensor == null) {
            log.warn("[CalibRunService] No sensor resolved (sensor_id={}, mu_id={}) — coefficients will NOT be updated",
                    req.getSensorId(), req.getMuId());
        }

        // Read current DB coefficients to pass as --old-a/b/c/d to the Python script.
        // null = not yet calibrated (first run) → Python uses its engine defaults.
        Double oldA = (sensor != null) ? sensor.getCoeffA() : null;
        Double oldB = (sensor != null) ? sensor.getCoeffB() : null;
        Double oldC = (sensor != null) ? sensor.getCoeffC() : null;
        Double oldD = (sensor != null) ? sensor.getCoeffD() : null;

        log.info("[CalibRunService] resolved sensor.id={}, coeffs from DB: A={} B={} C={} D={}",
                sensor != null ? sensor.getId() : null, oldA, oldB, oldC, oldD);

        // R18/R19: look up previous successful calibration for this sensor to feed rmse_pre as ufit
        Double ufit = null;
        if (calib.getMuId() != null) {
            var prevCalib = calibrationRepo.findTopByMuIdAndRunStatusAndIdNotOrderByCreatedAtDesc(
                    calib.getMuId(), "SUCCESS", calib.getId());
            if (prevCalib.isPresent()) {
                String prevJson = prevCalib.get().getLastCalibrationJson();
                if (prevJson != null && !prevJson.isBlank()) {
                    try {
                        var mapper = new ObjectMapper();
                        var node = mapper.readTree(prevJson);
                        if (node.has("fit_quality") && node.get("fit_quality").has("rmse_pre")) {
                            ufit = node.get("fit_quality").get("rmse_pre").asDouble();
                            log.info("[CalibRunService] Previous calibration rmse_pre={} → will inject as ufit in sensor JSON", ufit);
                        }
                    } catch (Exception e) {
                        log.warn("[CalibRunService] Could not parse previous calibration JSON: {}", e.getMessage());
                    }
                }
            }
        }

        String runId = deriveRunId(calib);
        Path runsBase   = resolveRunsDir();
        Path runDir     = runsBase.resolve(runId);
        Path inputDir   = runDir.resolve("input");
        Path outputDir  = runDir.resolve("output");
        Path imagesDir  = runDir.resolve("images");

        try {
            // 1. Create directory structure
            Files.createDirectories(inputDir);
            Files.createDirectories(outputDir);
            Files.createDirectories(imagesDir);

            // 2. Write input files
            Path inputJsonPath = inputDir.resolve("export.json");
            Files.writeString(inputJsonPath, req.getProcessedJson(), StandardCharsets.UTF_8);

            Path certInputPath = inputDir.resolve("certificato_in.json");
            Files.writeString(certInputPath, calib.getCertificatoIn(), StandardCharsets.UTF_8);

            // 3. Resolve script and model paths
            Path scriptPath   = resolveScript();
            Path modelsDir    = resolveModelsDir();
            Path sensorPath   = modelsDir.resolve("sensors").resolve(
                    config.getSensorJson() != null ? config.getSensorJson() : "ntc_temperature.json");
            Path refPath      = modelsDir.resolve("references").resolve(
                    config.getRefJson() != null ? config.getRefJson() : "fluke_9142.json");

            // R18/R19: inject rmse_pre from previous calibration as ufit into sensor JSON
            if (ufit != null && Files.exists(sensorPath)) {
                try {
                    var mapper = new ObjectMapper();
                    var sensorNode = mapper.readTree(sensorPath.toFile());
                    var ruArray = sensorNode.at("/metrology/readingUncertainty");
                    if (ruArray.isArray()) {
                        // Inject or update ufit entry
                        boolean found = false;
                        for (var item : ruArray) {
                            if ("ufit".equals(item.get("varName").asText())) {
                                ((com.fasterxml.jackson.databind.node.ObjectNode) item).put("value", ufit);
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            var ufitEntry = mapper.createObjectNode();
                            ufitEntry.put("varName", "ufit");
                            ufitEntry.put("value", ufit);
                            ufitEntry.put("coverageFactor", 2.0);
                            ufitEntry.put("PDF", "normal");
                            ufitEntry.put("description", "Injected from previous calibration rmse_pre");
                            ((com.fasterxml.jackson.databind.node.ArrayNode) ruArray).add(ufitEntry);
                        }
                        Path patchedSensorPath = inputDir.resolve("sensor_patched.json");
                        mapper.writerWithDefaultPrettyPrinter().writeValue(patchedSensorPath.toFile(), sensorNode);
                        sensorPath = patchedSensorPath;
                        log.info("[CalibRunService] Patched sensor JSON with ufit={} → {}", ufit, patchedSensorPath);
                    }
                } catch (Exception e) {
                    log.warn("[CalibRunService] Could not patch sensor JSON with ufit: {}", e.getMessage());
                }
            }

            Path certOutputPath     = outputDir.resolve("certificato_funzione_filled.json");
            Path pdfOutputPath      = outputDir.resolve("ntc_cert_funzione.pdf");
            Path xmlOutputPath      = outputDir.resolve("ntc_calibration_certificate.xml");
            Path conformityPath     = outputDir.resolve("conformity.json");
            Path lastCalibPath      = outputDir.resolve("result_calibration.json");

            // 4. Mark as RUNNING
            calib.setRunId(runId);
            calib.setRunStatus("RUNNING");
            calibrationRepo.save(calib);

            // 5. Launch Python — pass DB coefficients as --old-a/b/c/d
            PythonBridgeService.CalibrationRunResult result = pythonBridge.runCalibration(
                    scriptPath.toString(),
                    inputJsonPath.toString(),
                    sensorPath.toString(),
                    refPath.toString(),
                    certInputPath.toString(),
                    certOutputPath.toString(),
                    pdfOutputPath.toString(),
                    xmlOutputPath.toString(),
                    conformityPath.toString(),
                    imagesDir.toString(),
                    config,
                    oldA, oldB, oldC, oldD,
                    lastCalibPath.toString()
            );

            // 5a. Recursively upload all generated files under output/ and images/ to S3
            boolean s3Available = false;
            if (s3Service.isAvailable()) {
                try {
                    String s3Prefix = S3_KEY_PREFIX + runId + "/";
                    uploadDirectoryToS3(outputDir, s3Prefix + "output");
                    uploadDirectoryToS3(imagesDir, s3Prefix + "images");
                    s3Available = true;
                    log.info("[CalibRunService] S3 upload completed for run {}", runId);
                } catch (Exception e) {
                    log.warn("[CalibRunService] S3 upload failed, falling back to local: {}", e.getMessage());
                }
            } else {
                log.warn("[CalibRunService] S3 not available, files only stored locally");
            }

            // 6. Collect output file contents and file URLs (recursive, any name)
            String resultJson     = readIfExists(certOutputPath);
            String conformityJson = readIfExists(conformityPath);
            String dccXml         = readIfExists(xmlOutputPath);
            String lastCalibJson  = readIfExists(lastCalibPath);
            List<String> fileUrls = collectFileUrls(runId, runDir, s3Available);
            String imagesJson = new ObjectMapper().writeValueAsString(fileUrls);

            String pdfUrl = null;
            if (s3Available) {
                pdfUrl = findFirstPdfUrl(runId, outputDir, true);
            }
            if (pdfUrl == null) {
                pdfUrl = findFirstPdfUrl(runId, outputDir, false);
            }

            // 7. Persist calibration results
            calib.setRunLog(result.log());
            calib.setRunStatus(result.exitCode() == 0 ? "SUCCESS" : "FAILED");
            calib.setResultJson(resultJson);
            calib.setConformityJson(conformityJson);
            calib.setDccXml(dccXml);
            calib.setImages(imagesJson);
            calib.setPdfOutputUrl(pdfUrl);
            calib.setLastCalibrationJson(lastCalibJson);
            calibrationRepo.save(calib);

            // 8. On success: extract new coefficients from resultJson and persist to Sensor
            //    Runs in a REQUIRES_NEW transaction so it commits even if the outer
            //    transaction (held for the Python subprocess duration) has timed out.
            if (result.exitCode() == 0 && resultJson != null && sensor != null) {
                sensorCoeffUpdater.update(sensor.getId(), resultJson);
            }

            log.info("[CalibRunService] run {} completed with exit={}", runId, result.exitCode());
            return wizardService.toDto(calib);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            calib.setRunStatus("FAILED");
            calib.setRunLog("Exception: " + e.getMessage());
            calibrationRepo.save(calib);
            throw new RuntimeException("Calibration run error: " + e.getMessage(), e);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Derives the run ID from the linked CalibrationRequest's calibrationId, or uses "calib-<id>" fallback. */
    private String deriveRunId(Calibration calib) {
        if (calib.getCalibrationRequestId() != null) {
            return requestRepo.findById(calib.getCalibrationRequestId())
                    .map(r -> r.getCalibrationId() != null && !r.getCalibrationId().isBlank() ? r.getCalibrationId() : "calib-" + calib.getId())
                    .orElse("calib-" + calib.getId());
        }
        return "calib-" + calib.getId();
    }

    /** Resolves the calibration script path (from config or auto-detect). */
    private Path resolveScript() {
        if (calibrationScriptPath != null && !calibrationScriptPath.isBlank()) {
            return Path.of(calibrationScriptPath).toAbsolutePath();
        }
        // Try to find the script relative to the working directory (dev layout)
        Path candidate = Path.of("../calibration/scripts/analisi_calib_data.py").toAbsolutePath().normalize();
        if (Files.exists(candidate)) return candidate;
        throw new IllegalStateException(
                "analisi_calib_data.py not found. Set CALIBRATION_SCRIPT_PATH env var or place it at " + candidate);
    }

    /** Resolves the models_in directory. */
    private Path resolveModelsDir() {
        if (calibrationModelsPath != null && !calibrationModelsPath.isBlank()) {
            return Path.of(calibrationModelsPath).toAbsolutePath();
        }
        Path candidate = Path.of("../calibration/models_in").toAbsolutePath().normalize();
        if (Files.exists(candidate)) return candidate;
        throw new IllegalStateException(
                "models_in directory not found. Set CALIBRATION_MODELS_PATH env var or place it at " + candidate);
    }

    /** Resolves (and creates if needed) the runs base directory. */
    private Path resolveRunsDir() {
        Path dir = Path.of(calibrationRunsPath).toAbsolutePath().normalize();
        try { Files.createDirectories(dir); } catch (IOException e) { throw new RuntimeException(e); }
        return dir;
    }

    /**
     * Returns the list of sensor template JSON file names from models_in/sensors/.
     * Used by the standalone verify-conformity endpoint (no calibration ID required).
     */
    public List<String> listSensorTemplates() {
        return listJsonFiles(resolveModelsDir().resolve("sensors"));
    }

    /**
     * Returns the absolute path to a run's output directory.
     * Used by the static file server.
     */
    public Path resolveRunOutputPath(String runId, String... segments) {
        Path base = resolveRunsDir().resolve(runId);
        for (String seg : segments) base = base.resolve(seg);
        return base;
    }

    /** Lists .json file names in a directory (non-recursive). */
    private List<String> listJsonFiles(Path dir) {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(dir)) return names;
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.toString().endsWith(".json"))
             .map(p -> p.getFileName().toString())
             .sorted()
             .forEach(names::add);
        } catch (IOException ignored) {}
        return names;
    }

    /** Reads a file as UTF-8 string; returns null if missing. */
    private String readIfExists(Path p) {
        try {
            return Files.exists(p) ? Files.readString(p, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            log.warn("[CalibRunService] Could not read {}: {}", p, e.getMessage());
            return null;
        }
    }

    /**
     * Recursively scans the images/ directory under runDir and returns URL paths
     * for all plot images. Uses S3 URLs when available, local static URLs as fallback.
     * Skips input/ and output/ (those are non-plot artifacts served via dedicated endpoints).
     */
    private List<String> collectFileUrls(String runId, Path runDir, boolean s3Available) {
        List<String> urls = new ArrayList<>();
        String base = s3Available ? S3_BASE : STATIC_BASE;
        for (String root : List.of("images")) {
            Path rootDir = runDir.resolve(root);
            if (!Files.isDirectory(rootDir)) continue;
            try (Stream<Path> stream = Files.walk(rootDir)) {
                stream.filter(Files::isRegularFile)
                      .map(p -> base + runId + "/" + runDir.relativize(p).toString().replace('\\', '/'))
                      .sorted()
                      .forEach(urls::add);
            } catch (IOException ignored) {}
        }
        return urls;
    }

    /** Recursively uploads all files under localDir to S3 under the given s3Prefix. */
    private void uploadDirectoryToS3(Path localDir, String s3Prefix) throws IOException {
        if (!Files.isDirectory(localDir)) return;
        try (Stream<Path> stream = Files.walk(localDir)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                String relPath = localDir.relativize(p).toString().replace('\\', '/');
                String s3Key = s3Prefix + "/" + relPath;
                String contentType = detectContentType(p.getFileName().toString());
                s3Service.uploadPath(s3Key, p, contentType);
            });
        }
    }

    /** Finds the first .pdf file under outputDir and returns its S3 or static URL. */
    private String findFirstPdfUrl(String runId, Path outputDir, boolean s3) {
        if (!Files.isDirectory(outputDir)) return null;
        try (Stream<Path> stream = Files.walk(outputDir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".pdf"))
                    .findFirst()
                    .map(p -> {
                        String base = s3 ? S3_BASE : STATIC_BASE;
                        String rel = outputDir.getParent().relativize(p).toString().replace('\\', '/');
                        return base + runId + "/" + rel;
                    })
                    .orElse(null);
        } catch (IOException ignored) {
            return null;
        }
    }

    private String detectContentType(String filename) {
        if (filename.endsWith(".png"))  return "image/png";
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) return "image/jpeg";
        if (filename.endsWith(".pdf"))  return "application/pdf";
        if (filename.endsWith(".xml"))  return "application/xml";
        if (filename.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }
}
