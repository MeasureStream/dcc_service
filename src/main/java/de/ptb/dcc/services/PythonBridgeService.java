package de.ptb.dcc.services;

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
import java.util.Comparator;

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
}
