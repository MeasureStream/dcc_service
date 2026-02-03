package de.ptb.dcc.services;

import de.ptb.dcc.entities.Dcc;
import de.ptb.dcc.repositories.DccRepository;
import de.ptb.dcc.utils.SigningUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

@Slf4j
@Service
public class DccSigningService {

    private final DccRepository dccRepository;

    public DccSigningService(DccRepository dccRepository) {
        this.dccRepository = dccRepository;
    }

    @Transactional
    public Dcc performSigningAndVerification(Dcc dcc, String xmlContent, byte[] pdfContent) {
        try {
            log.info("Starting Signing and Verification for DCC ID: {}", dcc.getId());

            File tempXml = File.createTempFile("dcc-", ".xml");
            File tempPdf = File.createTempFile("dcc-", ".pdf");
            Files.writeString(tempXml.toPath(), xmlContent);
            Files.write(tempPdf.toPath(), pdfContent);

            File signedXml = File.createTempFile("dcc-signed-", ".xml");
            File signedPdf = File.createTempFile("dcc-signed-", ".pdf");

            // 1. Load Keys
            PrivateKey privateKey = SigningUtils.loadPrivateKey();
            X509Certificate cert = SigningUtils.loadCertificate();

            // 2. Signing
            log.info("Signing XML for DCC ID: {}...", dcc.getId());
            String hashXml = SigningUtils.signXml(tempXml, signedXml, privateKey, cert);

            log.info("Signing PDF for DCC ID: {}...", dcc.getId());
            String hashPdf = SigningUtils.signPdf(tempPdf.getAbsolutePath(), signedPdf.getAbsolutePath(), privateKey, cert);

            // 3. Final Verification
            log.info("Verifying signed XML for DCC ID: {}...", dcc.getId());
            boolean xmlValid = SigningUtils.verifyXml(signedXml);

            log.info("Verifying signed PDF for DCC ID: {}...", dcc.getId());
            boolean pdfValid = hashPdf != null && signedPdf.exists() && signedPdf.length() > 0;

            // 4. Update DCC entity
            dcc.setXmlValid(xmlValid);
            dcc.setPdfValid(pdfValid);
            dcc.setHashXml(hashXml);
            dcc.setHashPdf(hashPdf);

            log.info("DCC ID {} validation results - XML: {}, PDF: {}", dcc.getId(), xmlValid, pdfValid);

            // Cleanup
            tempXml.delete();
            tempPdf.delete();
            signedXml.delete();
            signedPdf.delete();

            return dccRepository.save(dcc);

        } catch (Exception e) {
            log.error("Signing/Verification failed for DCC ID {}: {}", dcc.getId(), e.getMessage(), e);
            throw new RuntimeException("Signing/Verification failed: " + e.getMessage(), e);
        }
    }
}
