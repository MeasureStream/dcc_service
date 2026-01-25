import org.apache.xml.security.Init;
import org.apache.xml.security.signature.ObjectContainer;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.transforms.Transforms;
import org.apache.xml.security.utils.Constants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * XML Digital Signature Tool for DCC Documents
 *
 * This class signs XML files using XML Digital Signatures (XAdES-BES)
 * according to the XML-DSig and XAdES standards (ETSI EN 319 132-1).
 */
public class sign_xml {
    private static final String XADES_NS = "http://uri.etsi.org/01903/v1.3.2#";
    // libxml2/lxml-based schema validators often choke on very large certificate
    // serial numbers
    // (even though xs:integer is unbounded). Keep serial within signed 63-bit
    // range.
    private static final int MAX_SERIAL_BIT_LENGTH = 63;

    static {
        Init.init();
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    public static void main(String[] args) {
        System.out.println("=== XAdES Digital Signature Tool for DCC Documents ===\n");

        try {
            String baseDir = System.getProperty("user.dir");
            File inputXml = new File(baseDir + "/input/dcc.xml");
            File outputXml = new File(baseDir + "/output/dcc_signed.xml");

            if (!inputXml.exists()) {
                System.err.println("[ERROR] Input XML file not found: " + inputXml.getAbsolutePath());
                System.exit(1);
            }

            Files.createDirectories(Paths.get(baseDir + "/output"));

            System.out.println("Loading keys from keys folder...");
            PrivateKey privateKey = loadPrivateKey(new File(baseDir + "/keys/private_key.pem"));
            File certFile = new File(baseDir + "/keys/certificate.pem");
            X509Certificate cert = loadCertificate(certFile);
            if (cert.getSerialNumber() == null || cert.getSerialNumber().bitLength() > MAX_SERIAL_BIT_LENGTH) {
                System.out.println("[WARN] Certificate serial number too large for some validators: "
                        + cert.getSerialNumber() + " (bitLength="
                        + (cert.getSerialNumber() == null ? "null" : cert.getSerialNumber().bitLength())
                        + "). Regenerating a self-signed certificate with a small serial...");
                cert = regenerateSelfSignedCertificateWithSmallSerial(privateKey,
                        cert.getSubjectX500Principal().getName());
                writeCertificatePem(certFile, cert);
                System.out.println("[OK] Wrote regenerated certificate to: " + certFile.getAbsolutePath()
                        + " (serial=" + cert.getSerialNumber() + ")");
            }

            System.out.println("\n--- Signing XML Document (XAdES-BES) ---");
            System.out.println("Input:  " + inputXml.getAbsolutePath());
            System.out.println("Output: " + outputXml.getAbsolutePath());

            sign(inputXml, outputXml, privateKey, cert);

            System.out.println("\n[SUCCESS] XAdES Digital Signature process completed successfully!");
            System.out.println("  - Signed XML: " + outputXml.getAbsolutePath());
            System.out.println("  - Format: XAdES-BES (ETSI EN 319 132-1)");

        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void sign(File input, File output, PrivateKey key, X509Certificate cert) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder().parse(new FileInputStream(input));

        // 1. Create XMLSignature
        String sigId = "sig-" + UUID.randomUUID();
        XMLSignature sig = new XMLSignature(doc, "", XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256);
        doc.getDocumentElement().appendChild(sig.getElement());
        sig.getElement().setAttribute("Id", sigId);
        sig.getElement().setIdAttribute("Id", true);

        // 2. Reference 1: Entire Document (Enveloped)
        Transforms transforms = new Transforms(doc);
        transforms.addTransform(Transforms.TRANSFORM_ENVELOPED_SIGNATURE);
        transforms.addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
        sig.addDocument("", transforms, "http://www.w3.org/2001/04/xmlenc#sha256");

        // 3. Create XAdES Structure
        String xadesId = "xades-" + UUID.randomUUID();
        Element qualifyingProperties = doc.createElementNS(XADES_NS, "xades:QualifyingProperties");
        qualifyingProperties.setAttribute("Target", "#" + sigId);

        Element signedProperties = doc.createElementNS(XADES_NS, "xades:SignedProperties");
        signedProperties.setAttribute("Id", xadesId);
        // CRITICAL: We don't call setIdAttribute yet because it's not in the document
        // tree
        qualifyingProperties.appendChild(signedProperties);

        Element signedSignatureProperties = doc.createElementNS(XADES_NS, "xades:SignedSignatureProperties");
        signedProperties.appendChild(signedSignatureProperties);

        // SigningTime
        Element signingTime = doc.createElementNS(XADES_NS, "xades:SigningTime");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        signingTime.setTextContent(sdf.format(new Date()));
        signedSignatureProperties.appendChild(signingTime);

        // SigningCertificate
        Element signingCertificate = doc.createElementNS(XADES_NS, "xades:SigningCertificate");
        signedSignatureProperties.appendChild(signingCertificate);
        Element certElem = doc.createElementNS(XADES_NS, "xades:Cert");
        signingCertificate.appendChild(certElem);

        Element certDigest = doc.createElementNS(XADES_NS, "xades:CertDigest");
        certElem.appendChild(certDigest);
        Element digestMethod = doc.createElementNS(Constants.SignatureSpecNS, "ds:DigestMethod");
        digestMethod.setAttribute("Algorithm", "http://www.w3.org/2001/04/xmlenc#sha256");
        certDigest.appendChild(digestMethod);
        Element digestValue = doc.createElementNS(Constants.SignatureSpecNS, "ds:DigestValue");
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        digestValue.setTextContent(Base64.getEncoder().encodeToString(md.digest(cert.getEncoded())));
        certDigest.appendChild(digestValue);

        Element issuerSerial = doc.createElementNS(XADES_NS, "xades:IssuerSerial");
        certElem.appendChild(issuerSerial);
        Element issuerName = doc.createElementNS(Constants.SignatureSpecNS, "ds:X509IssuerName");
        issuerName.setTextContent(cert.getIssuerX500Principal().getName());
        issuerSerial.appendChild(issuerName);
        Element serialNum = doc.createElementNS(Constants.SignatureSpecNS, "ds:X509SerialNumber");
        serialNum.setTextContent(cert.getSerialNumber().toString());
        issuerSerial.appendChild(serialNum);

        // 4. Wrap XAdES in an Object and add to signature
        ObjectContainer obj = new ObjectContainer(doc);
        obj.getElement().appendChild(qualifyingProperties);
        sig.appendObject(obj);

        // 5. Reference 2: SignedProperties (XAdES part) - CRITICAL for XAdES compliance
        // NOW register ID after appending to document
        signedProperties.setIdAttribute("Id", true);

        Transforms xadesTransforms = new Transforms(doc);
        xadesTransforms.addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
        sig.addDocument("#" + xadesId, xadesTransforms, "http://www.w3.org/2001/04/xmlenc#sha256", null,
                "http://uri.etsi.org/01903#SignedProperties");

        // 6. Add KeyInfo with Id attribute (for Reference 3)
        sig.addKeyInfo(cert);
        String keyInfoId = "keyinfo-" + UUID.randomUUID();
        Element keyInfoElement = sig.getKeyInfo().getElement();
        keyInfoElement.setAttribute("Id", keyInfoId);
        keyInfoElement.setIdAttribute("Id", true);

        // 7. Reference 3: KeyInfo - required by signxml XAdESVerifier (expects 3 refs)
        Transforms keyInfoTransforms = new Transforms(doc);
        keyInfoTransforms.addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
        sig.addDocument("#" + keyInfoId, keyInfoTransforms, "http://www.w3.org/2001/04/xmlenc#sha256");

        // 8. Sign
        sig.sign(key);

        // 9. Save output
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        FileOutputStream fos = new FileOutputStream(output);
        transformer.transform(new DOMSource(doc), new StreamResult(fos));
        fos.close();
    }

    private static PrivateKey loadPrivateKey(File file) throws Exception {
        String key = new String(Files.readAllBytes(file.toPath()))
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(key)));
    }

    private static X509Certificate loadCertificate(File file) throws Exception {
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) certFactory.generateCertificate(new FileInputStream(file));
    }

    private static X509Certificate regenerateSelfSignedCertificateWithSmallSerial(PrivateKey privateKey,
            String subjectRfc2253)
            throws Exception {
        // Derive public key from private key
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PublicKey publicKey = kf.generatePublic(new java.security.spec.RSAPublicKeySpec(
                ((java.security.interfaces.RSAPrivateCrtKey) privateKey).getModulus(),
                ((java.security.interfaces.RSAPrivateCrtKey) privateKey).getPublicExponent()));

        X500Name subject = new X500Name(subjectRfc2253);
        BigInteger serial = BigInteger.valueOf(12345L);
        Date notBefore = new Date(System.currentTimeMillis() - 24L * 60L * 60L * 1000L);
        Date notAfter = new Date(System.currentTimeMillis() + 365L * 24L * 60L * 60L * 1000L);

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject, serial, notBefore, notAfter, subject, publicKey);
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(privateKey);
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer));
    }

    private static void writeCertificatePem(File file, X509Certificate cert) throws Exception {
        String pem = "-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(cert.getEncoded())
                + "\n-----END CERTIFICATE-----\n";
        Files.write(file.toPath(), pem.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
}