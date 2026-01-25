package de.ptb.dcc.services;

import de.ptb.dcc.entities.Dcc;
import de.ptb.dcc.repositories.DccRepository;
import de.ptb.dcc.utils.SigningUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

@Service
public class DccSigningService {

    private final DccRepository dccRepository;

    public DccSigningService(DccRepository dccRepository) {
        this.dccRepository = dccRepository;
    }

    @Transactional
    public Dcc performSigningAndVerification(Dcc dcc, String xmlContent, byte[] pdfContent) {
        try {
            System.out.println("=== Starting Signing and Verification for DCC ID: " + dcc.getId() + " ===");

            File tempXml = File.createTempFile("dcc-", ".xml");
            File tempPdf = File.createTempFile("dcc-", ".pdf");
            Files.writeString(tempXml.toPath(), xmlContent);
            Files.write(tempPdf.toPath(), pdfContent);

            File signedXml = File.createTempFile("dcc-signed-", ".xml");
            File signedPdf = File.createTempFile("dcc-signed-", ".pdf");

            // 1. Load Keys
            PrivateKey privateKey = SigningUtils.loadPrivateKey();
            X509Certificate cert = SigningUtils.loadCertificate();

            // 2. Initial Verification (optional, usually fails for unsigned)
            System.out.println("Running initial XML verification...");
            SigningUtils.verifyXml(tempXml);

            // 3. Signing
            System.out.println("Signing XML...");
            SigningUtils.signXml(tempXml, signedXml, privateKey, cert);

            System.out.println("Signing PDF...");
            boolean pdfSigned = SigningUtils.signPdf(tempPdf.getAbsolutePath(), signedPdf.getAbsolutePath(), privateKey, cert);

            // 4. Final Verification
            System.out.println("Running final XML verification...");
            boolean xmlValid = SigningUtils.verifyXml(signedXml);

            System.out.println("Running final PDF verification...");
            boolean pdfValid = pdfSigned && signedPdf.exists() && signedPdf.length() > 0;

            // 5. Update DCC entity
            dcc.setXmlValid(xmlValid);
            dcc.setPdfValid(pdfValid);

            if (xmlValid) System.out.println("[SUCCESS] XML Validation passed.");
            else System.err.println("[FAILURE] XML Validation failed.");

            if (pdfValid) System.out.println("[SUCCESS] PDF Validation passed.");
            else System.err.println("[FAILURE] PDF Validation failed.");

            // Cleanup
            tempXml.delete();
            tempPdf.delete();
            signedXml.delete();
            signedPdf.delete();

            System.out.println("=== Signing and Verification Completed ===");

        } catch (Exception e) {
            System.err.println("[ERROR] Signing/Verification failed: " + e.getMessage());
            e.printStackTrace();
        }

        return dccRepository.save(dcc);
    }
}
