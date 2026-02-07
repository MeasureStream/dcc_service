package de.ptb.dcc.services;

import de.ptb.dcc.entities.Dcc;
import de.ptb.dcc.utils.SigningUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

@Service
public class DccSigningService {

    public static class SigningResult {
        public final boolean xmlValid;
        public final boolean pdfValid;
        public final String hashXml;
        public final String hashPdf;
        public final File signedXml;
        public final File signedPdf;

        public SigningResult(boolean xmlValid, boolean pdfValid, String hashXml, String hashPdf, File signedXml,
                File signedPdf) {
            this.xmlValid = xmlValid;
            this.pdfValid = pdfValid;
            this.hashXml = hashXml;
            this.hashPdf = hashPdf;
            this.signedXml = signedXml;
            this.signedPdf = signedPdf;
        }
    }

    public SigningResult performSigningAndVerification(Dcc dcc, String xmlContent, byte[] pdfContent) {
        File signedXml = null;
        File signedPdf = null;
        try {
            System.out.println("=== Starting Signing and Verification for DCC ID: " + dcc.getId() + " ===");

            File tempXml = File.createTempFile("dcc-", ".xml");
            File tempPdf = File.createTempFile("dcc-", ".pdf");
            Files.writeString(tempXml.toPath(), xmlContent);
            Files.write(tempPdf.toPath(), pdfContent);

            signedXml = File.createTempFile("dcc-signed-", ".xml");
            signedPdf = File.createTempFile("dcc-signed-", ".pdf");

            // 1. Load Keys
            PrivateKey privateKey = SigningUtils.loadPrivateKey();
            X509Certificate cert = SigningUtils.loadCertificate();

            // 2. Initial Verification (optional, usually fails for unsigned)
            System.out.println("Running initial XML verification...");
            SigningUtils.verifyXml(tempXml);

            // 3. Signing
            System.out.println("Signing XML...");
            String hashXml = SigningUtils.signXml(tempXml, signedXml, privateKey, cert);

            System.out.println("Signing PDF...");
            String hashPdf = SigningUtils.signPdf(tempPdf.getAbsolutePath(), signedPdf.getAbsolutePath(), privateKey,
                    cert);

            // 4. Final Verification
            System.out.println("Running final XML verification...");
            boolean xmlValid = SigningUtils.verifyXml(signedXml);

            System.out.println("Running final PDF verification...");
            boolean pdfValid = hashPdf != null && signedPdf.exists() && signedPdf.length() > 0;

            if (xmlValid)
                System.out.println("[SUCCESS] XML Validation passed.");
            else
                System.err.println("[FAILURE] XML Validation failed.");

            if (pdfValid)
                System.out.println("[SUCCESS] PDF Validation passed.");
            else
                System.err.println("[FAILURE] PDF Validation failed.");

            // Cleanup temp files (not the signed ones, they will be returned)
            tempXml.delete();
            tempPdf.delete();

            System.out.println("=== Signing and Verification Completed ===");
            return new SigningResult(xmlValid, pdfValid, hashXml, hashPdf, signedXml, signedPdf);

        } catch (Exception e) {
            System.err.println("[ERROR] Signing/Verification failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
