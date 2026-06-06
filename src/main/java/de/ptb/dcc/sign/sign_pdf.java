import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.signatures.*;
import com.itextpdf.kernel.geom.Rectangle;

// segna  il PDF con chiave privata e certificato
public class sign_pdf {

    static {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    public static void main(String[] args) {
        System.out.println("=== PDF Digital Signature Tool for DCC Documents ===\n");

        try {
            String baseDir = System.getProperty("user.dir");
            String inputPdf = baseDir + "/input/Tesi_ChristianDellisanti.pdf";
            String outputPdf = baseDir + "/output/Tesi_ChristianDellisanti_signed.pdf";

            if (!Files.exists(Paths.get(inputPdf))) {
                System.err.println("[ERROR] Input PDF file not found: " + inputPdf);
                System.exit(1);
            }

            Files.createDirectories(Paths.get(baseDir + "/output"));

            KeyPair keyPair = loadKeys();
            X509Certificate certificate = loadCertificate();

            System.out.println("\n--- Signing PDF Document ---");
            System.out.println("Input:  " + inputPdf);
            System.out.println("Output: " + outputPdf);

            boolean success = signPdfFile(inputPdf, outputPdf, keyPair.getPrivate(), certificate);

            if (success) {
                System.out.println("\n[SUCCESS] PDF Digital Signature process completed successfully!");
                System.out.println("  - Signed PDF: " + outputPdf);
                System.out.println("  - Signature algorithm: RSA-SHA256");
                System.out.println("  - Format: PDF 2.0 compliant digital signature");
            } else {
                System.out.println("\n[ERROR] PDF signing failed");
                System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static KeyPair loadKeys() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        System.out.println("Loading keys from keys folder...");

        try {
            String privateKeyPem = new String(Files.readAllBytes(Paths.get("keys/private_key.pem")));
            privateKeyPem = privateKeyPem.replaceAll("\\n", "")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "");

            byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyPem);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

            String publicKeyPem = new String(Files.readAllBytes(Paths.get("keys/public_key.pem")));
            publicKeyPem = publicKeyPem.replaceAll("\\n", "")
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replace("-----BEGIN RSA PUBLIC KEY-----", "")
                    .replace("-----END RSA PUBLIC KEY-----", "");

            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyPem);
            java.security.spec.X509EncodedKeySpec publicKeySpec = new java.security.spec.X509EncodedKeySpec(
                    publicKeyBytes);
            PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

            System.out.println("[OK] Keys loaded successfully");
            return new KeyPair(publicKey, privateKey);

        } catch (Exception e) {
            System.err.println("[ERROR] Failed to load keys: " + e.getMessage());
            throw e;
        }
    }

    private static X509Certificate loadCertificate() throws IOException, CertificateException {
        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            FileInputStream fis = new FileInputStream("keys/certificate.pem");
            X509Certificate certificate = (X509Certificate) certFactory.generateCertificate(fis);
            fis.close();

            System.out.println("[OK] Certificate loaded successfully");
            return certificate;

        } catch (Exception e) {
            System.err.println("[ERROR] Failed to load certificate: " + e.getMessage());
            throw e;
        }
    }

    private static boolean signPdfFile(String inputPath, String outputPath, PrivateKey privateKey,
            X509Certificate certificate)
            throws Exception {

        try {
            PdfReader reader = new PdfReader(inputPath);
            FileOutputStream fos = new FileOutputStream(outputPath);

            IExternalSignature signature = new PrivateKeySignature(privateKey, DigestAlgorithms.SHA256, "BC");
            IExternalDigest digest = new BouncyCastleDigest();

            java.security.cert.Certificate[] certChain = new java.security.cert.Certificate[] { certificate };

            com.itextpdf.signatures.PdfSigner signer = new com.itextpdf.signatures.PdfSigner(reader, fos, new com.itextpdf.kernel.pdf.StampingProperties().useAppendMode());
            signer.setFieldName("Signature1");
            signer.getSignatureAppearance().setReason("DCC Document Signature");
            signer.getSignatureAppearance().setLocation("Physikalisch-Technische Bundesanstalt");
            signer.signDetached(digest, signature, certChain, null, null, null, 0, com.itextpdf.signatures.PdfSigner.CryptoStandard.CMS);

            reader.close();
            fos.close();

            System.out.println("[SUCCESS] Signed PDF saved to: " + outputPath);
            return true;

        } catch (Exception e) {
            System.err.println("[ERROR] Failed to sign PDF: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
