import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Test class to verify Java XML and PDF signing tools work correctly
 */
public class test_java_signing {

    public static void main(String[] args) {
        System.out.println("=== Testing Java XML and PDF Signing Tools ===\n");

        try {
            String baseDir = System.getProperty("user.dir");

            // Test XML signing
            System.out.println("--- Testing XML Signing ---");
            testXmlSigning(baseDir);

            // Test PDF signing
            System.out.println("--- Testing PDF Signing ---");
            testPdfSigning(baseDir);

            System.out.println("\n=== All Tests Completed ===");

        } catch (Exception e) {
            System.err.println("[ERROR] Test failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void testXmlSigning(String baseDir) throws Exception {
        String inputXml = baseDir + "/input/dcc.xml";
        String outputXml = baseDir + "/output/dcc_signed_java.xml";

        // Check if input exists
        if (!Files.exists(Paths.get(inputXml))) {
            System.out.println("[SKIP] XML input file not found: " + inputXml);
            return;
        }

        System.out.println("Running XML signing test...");
        System.out.println("Input: " + inputXml);
        System.out.println("Output: " + outputXml);

        // Run XML signing
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", getClasspath(), "sign_xml");
        pb.directory(new File(baseDir + "/sign"));
        pb.redirectErrorStream(true);

        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("[XML] " + line);
        }

        int exitCode = process.waitFor();
        if (exitCode == 0 && Files.exists(Paths.get(baseDir + "/sign/output/dcc_signed.xml"))) {
            System.out.println("[SUCCESS] XML signing completed");

            // Verify with Python tool
            verifyXmlSignature(baseDir);
        } else {
            System.out.println("[FAIL] XML signing failed with exit code: " + exitCode);
        }
    }

    private static void testPdfSigning(String baseDir) throws Exception {
        String inputPdf = baseDir + "/input/Tesi_ChristianDellisanti.pdf";
        String outputPdf = baseDir + "/output/Tesi_ChristianDellisanti_signed_java.pdf";

        // Check if input exists
        if (!Files.exists(Paths.get(inputPdf))) {
            System.out.println("[SKIP] PDF input file not found: " + inputPdf);
            return;
        }

        System.out.println("Running PDF signing test...");
        System.out.println("Input: " + inputPdf);
        System.out.println("Output: " + outputPdf);

        // Run PDF signing
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", getClasspath(), "sign_pdf");
        pb.directory(new File(baseDir + "/sign"));
        pb.redirectErrorStream(true);

        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("[PDF] " + line);
        }

        int exitCode = process.waitFor();
        if (exitCode == 0 && Files.exists(Paths.get(baseDir + "/sign/output/Tesi_ChristianDellisanti_signed.pdf"))) {
            System.out.println("[SUCCESS] PDF signing completed");

            // Verify with Python tool
            verifyPdfSignature(baseDir);
        } else {
            System.out.println("[FAIL] PDF signing failed with exit code: " + exitCode);
        }
    }

    private static void verifyXmlSignature(String baseDir) throws Exception {
        System.out.println("\n--- Verifying XML Signature ---");

        ProcessBuilder pb = new ProcessBuilder("python3", baseDir + "/verify/verify_xml_sig.py");
        pb.directory(new File(baseDir + "/verify"));
        pb.redirectErrorStream(true);

        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        boolean verified = false;
        while ((line = reader.readLine()) != null) {
            System.out.println("[VERIFY_XML] " + line);
            if (line.contains("[SUCCESS]") || line.contains("valid")) {
                verified = true;
            }
        }

        int exitCode = process.waitFor();
        if (exitCode == 0 && verified) {
            System.out.println("[SUCCESS] XML signature verification passed");
        } else {
            System.out.println("[FAIL] XML signature verification failed");
        }
    }

    private static void verifyPdfSignature(String baseDir) throws Exception {
        System.out.println("\n--- Verifying PDF Signature ---");

        // Use Python verification (you could implement Java verification here too)
        ProcessBuilder pb = new ProcessBuilder("python3", "-c", "print('PDF verification not implemented yet')");
        pb.redirectErrorStream(true);

        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("[VERIFY_PDF] " + line);
        }

        System.out.println("[INFO] PDF signature verification - check output file manually");
    }

    private static String getClasspath() {
        // This would need to include all the JAR files from Maven dependencies
        // For now, return a basic classpath
        return "target/classes";
    }
}
