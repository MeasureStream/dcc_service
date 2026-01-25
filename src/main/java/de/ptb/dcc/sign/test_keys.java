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

/**
 * Simple test to verify key loading works
 */
public class test_keys {

    public static void main(String[] args) {
        System.out.println("=== Testing Key Loading ===\n");

        try {
            // Test loading keys
            KeyPair keyPair = loadKeys();
            X509Certificate certificate = loadCertificate();

            System.out.println("[SUCCESS] Keys and certificate loaded successfully!");
            System.out.println("Private key algorithm: " + keyPair.getPrivate().getAlgorithm());
            System.out.println("Public key algorithm: " + keyPair.getPublic().getAlgorithm());
            System.out.println("Certificate subject: " + certificate.getSubjectDN());

        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static KeyPair loadKeys() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        System.out.println("Loading keys from keys folder...");

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
        java.security.spec.X509EncodedKeySpec publicKeySpec = new java.security.spec.X509EncodedKeySpec(publicKeyBytes);
        PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

        System.out.println("[OK] Keys loaded successfully");
        return new KeyPair(publicKey, privateKey);
    }

    private static X509Certificate loadCertificate() throws IOException, CertificateException {
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        FileInputStream fis = new FileInputStream("keys/certificate.pem");
        X509Certificate certificate = (X509Certificate) certFactory.generateCertificate(fis);
        fis.close();

        System.out.println("[OK] Certificate loaded successfully");
        return certificate;
    }
}


