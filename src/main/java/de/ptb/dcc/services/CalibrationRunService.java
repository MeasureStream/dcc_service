package de.ptb.dcc.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.ptb.dcc.dtos.CalibrationDto;
import de.ptb.dcc.dtos.CalibrationRunConfig;
import de.ptb.dcc.dtos.CalibrationRunConfigOptions;
import de.ptb.dcc.entities.Calibration;
import de.ptb.dcc.entities.CalibrationRequest;
import de.ptb.dcc.repositories.CalibrationRepository;
import de.ptb.dcc.repositories.CalibrationRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
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
    private static final String OUTPUT_CERT  = "certificato_funzione_filled.json";
    private static final String OUTPUT_PDF   = "ntc_cert_funzione.pdf";
    private static final String OUTPUT_XML   = "ntc_calibration_certificate.xml";
    private static final String OUTPUT_CONF  = "conformity.json";
    private static final String INPUT_EXPORT = "export.json";
    private static final String INPUT_CERT   = "certificato_in.json";

    @Value("${calibration.script.path:}")
    private String calibrationScriptPath;

    @Value("${calibration.models.path:}")
    private String calibrationModelsPath;

    @Value("${calibration.runs.path:./calibration-runs}")
    private String calibrationRunsPath;

    private final CalibrationRepository calibrationRepo;
    private final CalibrationRequestRepository requestRepo;
    private final PythonBridgeService pythonBridge;
    private final CalibrationWizardService wizardService;

    public CalibrationRunService(CalibrationRepository calibrationRepo,
                                  CalibrationRequestRepository requestRepo,
                                  PythonBridgeService pythonBridge,
                                  CalibrationWizardService wizardService) {
        this.calibrationRepo = calibrationRepo;
        this.requestRepo = requestRepo;
        this.pythonBridge = pythonBridge;
        this.wizardService = wizardService;
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
    @Transactional
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
            Files.createDirectories(imagesDir.resolve("calibration"));
            Files.createDirectories(imagesDir.resolve("conformity"));

            // 2. Write input files
            Path inputJsonPath = inputDir.resolve(INPUT_EXPORT);
            Files.writeString(inputJsonPath, req.getProcessedJson(), StandardCharsets.UTF_8);

            Path certInputPath = inputDir.resolve(INPUT_CERT);
            Files.writeString(certInputPath, calib.getCertificatoIn(), StandardCharsets.UTF_8);

            // 3. Resolve script and model paths
            Path scriptPath   = resolveScript();
            Path modelsDir    = resolveModelsDir();
            Path sensorPath   = modelsDir.resolve("sensors").resolve(
                    config.getSensorJson() != null ? config.getSensorJson() : "ntc_temperature.json");
            Path refPath      = modelsDir.resolve("references").resolve(
                    config.getRefJson() != null ? config.getRefJson() : "fluke_9142.json");

            Path certOutputPath     = outputDir.resolve(OUTPUT_CERT);
            Path pdfOutputPath      = outputDir.resolve(OUTPUT_PDF);
            Path xmlOutputPath      = outputDir.resolve(OUTPUT_XML);
            Path conformityPath     = outputDir.resolve(OUTPUT_CONF);

            // 4. Mark as RUNNING
            calib.setRunId(runId);
            calib.setRunStatus("RUNNING");
            calibrationRepo.save(calib);

            // 5. Launch Python
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
                    config
            );

            // 6. Collect output file contents and image URLs
            String resultJson     = readIfExists(certOutputPath);
            String conformityJson = readIfExists(conformityPath);
            String dccXml         = readIfExists(xmlOutputPath);
            List<String> imagePaths = collectImageUrls(runId, imagesDir);
            String imagesJson = new ObjectMapper().writeValueAsString(imagePaths);

            String pdfUrl = Files.exists(pdfOutputPath)
                    ? STATIC_BASE + runId + "/output/" + OUTPUT_PDF
                    : null;

            // 7. Persist results
            calib.setRunLog(result.log());
            calib.setRunStatus(result.exitCode() == 0 ? "SUCCESS" : "FAILED");
            calib.setResultJson(resultJson);
            calib.setConformityJson(conformityJson);
            calib.setDccXml(dccXml);
            calib.setImages(imagesJson);
            calib.setPdfOutputUrl(pdfUrl);
            calibrationRepo.save(calib);

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
                    .map(r -> r.getCalibrationId() != null ? r.getCalibrationId() : "calib-" + calib.getId())
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
     * Scans images/calibration/ and images/conformity/ for .png files
     * and returns their URL paths relative to the static endpoint.
     */
    private List<String> collectImageUrls(String runId, Path imagesDir) {
        List<String> urls = new ArrayList<>();
        for (String sub : List.of("calibration", "conformity")) {
            Path subDir = imagesDir.resolve(sub);
            if (!Files.isDirectory(subDir)) continue;
            try (Stream<Path> s = Files.list(subDir)) {
                s.filter(p -> p.toString().endsWith(".png"))
                 .map(p -> STATIC_BASE + runId + "/images/" + sub + "/" + p.getFileName())
                 .sorted()
                 .forEach(urls::add);
            } catch (IOException ignored) {}
        }
        return urls;
    }
}
