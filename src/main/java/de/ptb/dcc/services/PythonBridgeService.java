package de.ptb.dcc.services;

import de.ptb.dcc.dtos.CalibrationRunConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Unico punto di integrazione con Python.
 *
 * Il percorso dello script è configurabile via application.properties:
 *   python.cmd        = nome eseguibile python  (default: python, override via PYTHON_CMD)
 *   python.script.path = percorso filesystem     (default: vuoto → estrae da classpath)
 *
 * In futuro sostituibile con chiamata HTTP senza toccare altro codice.
 */
@Service
public class PythonBridgeService {

    private static final Logger log = LoggerFactory.getLogger(PythonBridgeService.class);

    @Value("${python.cmd:python}")
    private String pythonCmd;

    /** Se vuoto, lo script viene estratto dalla classpath automaticamente */
    @Value("${python.script.path:}")
    private String configuredScriptPath;

    /** Percorso assoluto a analisi_calib_data.py (configurabile via env CALIBRATION_SCRIPT_PATH) */
    @Value("${calibration.script.path:}")
    private String calibrationScriptPath;

    /** Percorso effettivo usato a runtime (risolto in @PostConstruct) */
    private String resolvedScriptPath;

    @PostConstruct
    public void resolveScript() {
        if (configuredScriptPath != null && !configuredScriptPath.isBlank()) {
            resolvedScriptPath = configuredScriptPath;
            log.info("[PythonBridge] Using configured script path: {}", resolvedScriptPath);
        } else {
            // Estrae build_input_json.py dalla classpath in una directory temporanea
            try {
                Path tmpScript = Files.createTempFile("build_input_json_", ".py");
                tmpScript.toFile().deleteOnExit();
                try (InputStream in = new ClassPathResource("build_input_json.py").getInputStream()) {
                    Files.copy(in, tmpScript, StandardCopyOption.REPLACE_EXISTING);
                }
                resolvedScriptPath = tmpScript.toAbsolutePath().toString();
                log.info("[PythonBridge] Extracted script from classpath to: {}", resolvedScriptPath);
            } catch (IOException e) {
                log.error("[PythonBridge] Could not extract build_input_json.py from classpath: {}", e.getMessage());
                resolvedScriptPath = "build_input_json.py"; // fallback
            }
        }
        log.info("[PythonBridge] python cmd: '{}', script: '{}'", pythonCmd, resolvedScriptPath);
    }

    /**
     * Esegue build_input_json.py con i 5 JSON forniti.
     *
     * Il script usa Path(__file__).parent come working dir per caricare i file.
     * Per questo copiamo lo script E tutti i JSON nella stessa tmpDir,
     * e passiamo solo i nomi file (non path assoluti) agli argomenti.
     */
    public String buildCertificatoIn(
            String baseInputJson,
            String calibrationMethodJson,
            String measurestreamCompanyJson,
            String clientCompanyJson,
            String jobJson
    ) throws IOException, InterruptedException {

        Path tmpDir = Files.createTempDirectory("calib_wizard_");
        try {
            // Copia lo script nella tmpDir così HERE = tmpDir
            Path scriptInTmp = tmpDir.resolve("build_input_json.py");
            Files.copy(Path.of(resolvedScriptPath), scriptInTmp, StandardCopyOption.REPLACE_EXISTING);

            // Scrivi tutti i JSON nella stessa directory dello script
            Files.writeString(tmpDir.resolve("calibration_method.json"),   calibrationMethodJson,   StandardCharsets.UTF_8);
            Files.writeString(tmpDir.resolve("client_company.json"),        clientCompanyJson,       StandardCharsets.UTF_8);
            Files.writeString(tmpDir.resolve("job.json"),                   jobJson,                 StandardCharsets.UTF_8);
            Files.writeString(tmpDir.resolve("measurestream_company.json"), measurestreamCompanyJson, StandardCharsets.UTF_8);
            Files.writeString(tmpDir.resolve("base_input.json"),            baseInputJson,           StandardCharsets.UTF_8);

            Path outFile = tmpDir.resolve("certificato_funzione_input.json");

            // Passa solo i nomi file — lo script li risolve relativamente a HERE (= tmpDir)
            ProcessBuilder pb = new ProcessBuilder(
                    pythonCmd,
                    scriptInTmp.toAbsolutePath().toString(),
                    "--method", "calibration_method.json",
                    "--client", "client_company.json",
                    "--job",    "job.json",
                    "--out",    outFile.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            pb.directory(tmpDir.toFile()); // working dir = tmpDir

            log.info("[PythonBridge] Running in '{}': {} build_input_json.py --method calibration_method.json --client client_company.json --job job.json --out {}",
                    tmpDir, pythonCmd, outFile);

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();

            log.info("[PythonBridge] exit={} output={}", exitCode, output);

            if (exitCode != 0) {
                throw new RuntimeException("build_input_json.py failed (exit " + exitCode + "):\n" + output);
            }

            if (!Files.exists(outFile)) {
                throw new RuntimeException("build_input_json.py succeeded but output file not found: " + outFile);
            }

            return Files.readString(outFile, StandardCharsets.UTF_8);

        } finally {
            try {
                Files.walk(tmpDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            } catch (IOException ignored) {}
        }
    }

    // ── Calibration run ────────────────────────────────────────────────────

    public record CalibrationRunResult(int exitCode, String log) {}

    /**
     * Launches analisi_calib_data.py as a subprocess.
     *
     * All paths are absolute and pre-created by CalibrationRunService.
     * The process runs with a 10-minute timeout; stdout+stderr are captured
     * and returned together in the result log.
     *
     * @param scriptPath  absolute path to analisi_calib_data.py
     * @param inputJson   absolute path to the measurement JSON (processedJson)
     * @param sensorJson  absolute path to the sensor model JSON
     * @param refJson     absolute path to the reference calibrator JSON
     * @param certInput   absolute path to certificato_in (certificatoIn from wizard)
     * @param certOutput  absolute path for certificato_funzione_filled.json output
     * @param pdfOutput   absolute path for PDF output
     * @param xmlOutput   absolute path for DCC XML output
     * @param conformityOutput absolute path for conformity JSON output
     * @param imagesDir   absolute path to the images base directory
     * @param config      user-selected CLI options
     * @param oldA        previous coefficient A from DB (null = first calibration, use defaults)
     * @param oldB        previous coefficient B from DB
     * @param oldC        previous coefficient C from DB (cubic a2 or cube-log C3)
     * @param oldD        previous coefficient D from DB (cubic a3)
     * @param lastCalibrationPath  absolute path for last_calibration.json output
     */
    public CalibrationRunResult runCalibration(
            String scriptPath,
            String inputJson,
            String sensorJson,
            String refJson,
            String certInput,
            String certOutput,
            String pdfOutput,
            String xmlOutput,
            String conformityOutput,
            String imagesDir,
            CalibrationRunConfig config,
            Double oldA,
            Double oldB,
            Double oldC,
            Double oldD,
            String lastCalibrationPath
    ) throws IOException, InterruptedException {

        List<String> cmd = new ArrayList<>();
        cmd.add(pythonCmd);
        cmd.add(scriptPath);
        cmd.add("--input");       cmd.add(inputJson);
        cmd.add("--sensor");      cmd.add(sensorJson);
        cmd.add("--ref");         cmd.add(refJson);
        cmd.add("--cert-input");  cmd.add(certInput);
        cmd.add("--cert-output"); cmd.add(certOutput);
        cmd.add("--pdf");         cmd.add(pdfOutput);
        cmd.add("--xml");         cmd.add(xmlOutput);
        cmd.add("--conformity-output"); cmd.add(conformityOutput);
        cmd.add("--images-dir");  cmd.add(imagesDir);

        // Inject previous calibration coefficients from DB so Python uses them as old_A/B/C/D
        // These override the 0.0 placeholders in the sensor JSON template.
        // C and D are only meaningful for cubic procedures; passing them to linear
        // causes argparse errors when values are malformed or null.
        boolean isCubic = config.getProcedure() != null
                && (config.getProcedure().equalsIgnoreCase("cubic")
                    || config.getProcedure().equalsIgnoreCase("cubic_interp")
                    || config.getProcedure().equalsIgnoreCase("cube-log"));
        if (oldA != null) { cmd.add("--old-a"); cmd.add(String.valueOf(oldA)); }
        if (oldB != null) { cmd.add("--old-b"); cmd.add(String.valueOf(oldB)); }
        if (isCubic && oldC != null) { cmd.add("--old-c"); cmd.add(String.valueOf(oldC)); }
        if (isCubic && oldD != null) { cmd.add("--old-d"); cmd.add(String.valueOf(oldD)); }

        // R18: last-calibration JSON output for downstream / next calibration
        if (lastCalibrationPath != null) {
            cmd.add("--last-calibration"); cmd.add(lastCalibrationPath);
        }

        if (config.getProcedure() != null && !config.getProcedure().isBlank()) {
            cmd.add("--procedure"); cmd.add(config.getProcedure());
        }
        if (config.isCharts())          { cmd.add("--charts"); }
        else                            { cmd.add("--no-charts"); }
        if (!config.isVerbose())       { cmd.add("--no-verbose"); }
        if (!"none".equals(config.getUpdateIfOutRange())) {
            cmd.add("--update-parameters"); cmd.add(config.getUpdateIfOutRange());
        }
        if (config.isCheckUnits())     { cmd.add("--check-units"); }
        if (config.isConvertUnits())   { cmd.add("--convert-units"); }
        if (config.isNoPdf())          { cmd.add("--no-pdf"); }
        if (config.isNoXml())          { cmd.add("--no-xml"); }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        // Run from the script's parent directory so relative imports work
        pb.directory(Path.of(scriptPath).getParent().toFile());

        log.info("[PythonBridge] runCalibration cmd: {}", cmd);

        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        // Wait up to 10 minutes
        boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.MINUTES);
        int exitCode = finished ? process.exitValue() : -1;
        if (!finished) {
            process.destroyForcibly();
            output += "\n[TIMEOUT] Process killed after 10 minutes.";
            log.error("[PythonBridge] runCalibration timed out");
        }

        log.info("[PythonBridge] runCalibration exit={} output_length={}", exitCode, output.length());
        return new CalibrationRunResult(exitCode, output);
    }
}
