package de.ptb.dcc.utils;

import org.apache.xml.security.Init;
import org.apache.xml.security.signature.ObjectContainer;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.transforms.Transforms;
import org.apache.xml.security.utils.Constants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import de.ptb.dcc.dtos.DccValidationResultDto;
import org.springframework.core.io.ClassPathResource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.*;

import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.signatures.*;

public class SigningUtils {

    private static final String XADES_NS = "http://uri.etsi.org/01903/v1.3.2#";

    static {
        Init.init();
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }
    }

    public static PrivateKey loadPrivateKey() throws Exception {
        try (InputStream is = new ClassPathResource("keys/private_key.pem").getInputStream()) {
            String key = new String(is.readAllBytes())
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(key)));
        }
    }

    public static X509Certificate loadCertificate() throws Exception {
        try (InputStream is = new ClassPathResource("keys/certificate.pem").getInputStream()) {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) certFactory.generateCertificate(is);
        }
    }

    public static String signXml(File input, File output, PrivateKey key, X509Certificate cert) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder().parse(new FileInputStream(input));

        String sigId = "sig-" + UUID.randomUUID();
        XMLSignature sig = new XMLSignature(doc, "", XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256);
        doc.getDocumentElement().appendChild(sig.getElement());
        sig.getElement().setAttribute("Id", sigId);
        sig.getElement().setIdAttribute("Id", true);

        Transforms transforms = new Transforms(doc);
        transforms.addTransform(Transforms.TRANSFORM_ENVELOPED_SIGNATURE);
        transforms.addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
        sig.addDocument("", transforms, "http://www.w3.org/2001/04/xmlenc#sha256");

        String xadesId = "xades-" + UUID.randomUUID();
        Element qualifyingProperties = doc.createElementNS(XADES_NS, "xades:QualifyingProperties");
        qualifyingProperties.setAttribute("Target", "#" + sigId);

        Element signedProperties = doc.createElementNS(XADES_NS, "xades:SignedProperties");
        signedProperties.setAttribute("Id", xadesId);
        qualifyingProperties.appendChild(signedProperties);

        Element signedSignatureProperties = doc.createElementNS(XADES_NS, "xades:SignedSignatureProperties");
        signedProperties.appendChild(signedSignatureProperties);

        Element signingTime = doc.createElementNS(XADES_NS, "xades:SigningTime");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        signingTime.setTextContent(sdf.format(new Date()));
        signedSignatureProperties.appendChild(signingTime);

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

        ObjectContainer obj = new ObjectContainer(doc);
        obj.getElement().appendChild(qualifyingProperties);
        sig.appendObject(obj);

        signedProperties.setIdAttribute("Id", true);

        Transforms xadesTransforms = new Transforms(doc);
        xadesTransforms.addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
        sig.addDocument("#" + xadesId, xadesTransforms, "http://www.w3.org/2001/04/xmlenc#sha256", null,
                "http://uri.etsi.org/01903#SignedProperties");

        sig.addKeyInfo(cert);
        String keyInfoId = "keyinfo-" + UUID.randomUUID();
        Element keyInfoElement = sig.getKeyInfo().getElement();
        keyInfoElement.setAttribute("Id", keyInfoId);
        keyInfoElement.setIdAttribute("Id", true);

        Transforms keyInfoTransforms = new Transforms(doc);
        keyInfoTransforms.addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
        sig.addDocument("#" + keyInfoId, keyInfoTransforms, "http://www.w3.org/2001/04/xmlenc#sha256");

        sig.sign(key);

        String hash = null;
        if (sig.getSignedInfo().getLength() > 0) {
            hash = Base64.getEncoder().encodeToString(sig.getSignedInfo().item(0).getDigestValue());
        }

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        try (FileOutputStream fos = new FileOutputStream(output)) {
            transformer.transform(new DOMSource(doc), new StreamResult(fos));
        }
        return hash;
    }

    public static boolean verifyXml(File file) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().parse(new FileInputStream(file));

            NodeList nodes = doc.getElementsByTagName("*");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                if (el.hasAttribute("Id"))
                    el.setIdAttribute("Id", true);
                if (el.hasAttribute("id"))
                    el.setIdAttribute("id", true);
            }

            Element sigElement = (Element) doc.getElementsByTagNameNS(Constants.SignatureSpecNS, "Signature").item(0);
            if (sigElement == null) {
                System.err.println("[ERROR] No ds:Signature element found.");
                return false;
            }

            XMLSignature sig = new XMLSignature(sigElement, "");
            X509Certificate cert = sig.getKeyInfo().getX509Certificate();
            if (cert == null) {
                System.err.println("[ERROR] No certificate found in ds:KeyInfo.");
                return false;
            }

            boolean valid = sig.checkSignatureValue(cert);
            if (valid) {
                System.out.println("[SUCCESS] XML Digital Signature is VALID.");
            } else {
                System.out.println("[FAILURE] XML Digital Signature is INVALID.");
            }
            return valid;
        } catch (Exception e) {
            System.err.println("[ERROR] Verification failed: " + e.getMessage());
            return false;
        }
    }

    public static String signPdf(String inputPath, String outputPath, PrivateKey privateKey,
            X509Certificate certificate) {
        try {
            PdfReader reader = new PdfReader(inputPath);
            FileOutputStream fos = new FileOutputStream(outputPath);
            IExternalSignature signature = new PrivateKeySignature(privateKey, DigestAlgorithms.SHA256, "BC");
            IExternalDigest digest = new BouncyCastleDigest();
            java.security.cert.Certificate[] certChain = new java.security.cert.Certificate[] { certificate };
            com.itextpdf.signatures.PdfSigner signer = new com.itextpdf.signatures.PdfSigner(reader, fos,
                    new com.itextpdf.kernel.pdf.StampingProperties().useAppendMode());
            signer.setFieldName("Signature1");
            signer.getSignatureAppearance().setReason("DCC Document Signature");
            signer.getSignatureAppearance().setLocation("Physikalisch-Technische Bundesanstalt");
            signer.signDetached(digest, signature, certChain, null, null, null, 0,
                    com.itextpdf.signatures.PdfSigner.CryptoStandard.CMS);
            reader.close();
            fos.close();

            // Extract hash from the newly signed file
            DccValidationResultDto result = validateExternalPdf(new File(outputPath));
            return result.getSignatureDetails() != null ? result.getSignatureDetails().getHash() : null;
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to sign PDF: " + e.getMessage());
            return null;
        }
    }

    public static DccValidationResultDto validateExternalXml(File file) {
        DccValidationResultDto result = new DccValidationResultDto();
        DccValidationResultDto.SignatureDetailsDto details = new DccValidationResultDto.SignatureDetailsDto();
        result.setSignatureDetails(details);

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().parse(new FileInputStream(file));

            NodeList nodes = doc.getElementsByTagName("*");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                if (el.hasAttribute("Id"))
                    el.setIdAttribute("Id", true);
                if (el.hasAttribute("id"))
                    el.setIdAttribute("id", true);
            }

            Element sigElement = (Element) doc.getElementsByTagNameNS(Constants.SignatureSpecNS, "Signature").item(0);
            if (sigElement == null) {
                result.setValid(false);
                return result;
            }

            XMLSignature sig = new XMLSignature(sigElement, "");
            X509Certificate cert = sig.getKeyInfo().getX509Certificate();

            boolean valid = sig.checkSignatureValue(cert);
            result.setValid(valid);

            details.setAlgorithm(sig.getSignedInfo().getSignatureMethodURI());
            if (cert != null) {
                details.setSigner(cert.getSubjectX500Principal().getName());
                details.setPublicKeyHash(calculatePublicKeyHash(cert.getPublicKey()));
                details.setPublicKeyMatch(true);
            }

            // Get the hash of the first reference (usually the main document)
            if (sig.getSignedInfo().getLength() > 0) {
                details.setHash(Base64.getEncoder().encodeToString(sig.getSignedInfo().item(0).getDigestValue()));
            }

            // Extract signing time if available (XAdES)
            NodeList signingTimeNodes = doc.getElementsByTagNameNS(XADES_NS, "SigningTime");
            if (signingTimeNodes.getLength() > 0) {
                details.setTimestamp(signingTimeNodes.item(0).getTextContent());
            } else {
                details.setTimestamp(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date()));
            }

        } catch (Exception e) {
            result.setValid(false);
            System.err.println("[ERROR] External XML verification failed: " + e.getMessage());
        }
        return result;
    }

    public static DccValidationResultDto validateExternalPdf(File file) {
        DccValidationResultDto result = new DccValidationResultDto();
        DccValidationResultDto.SignatureDetailsDto details = new DccValidationResultDto.SignatureDetailsDto();
        result.setSignatureDetails(details);

        try (PdfReader reader = new PdfReader(file);
                PdfDocument pdfDoc = new PdfDocument(reader)) {
            SignatureUtil signUtil = new SignatureUtil(pdfDoc);
            List<String> names = signUtil.getSignatureNames();

            if (names.isEmpty()) {
                result.setValid(false);
                return result;
            }

            String name = names.get(0);
            PdfPKCS7 pkcs7 = signUtil.readSignatureData(name);

            boolean valid = pkcs7.verifySignatureIntegrityAndAuthenticity();
            result.setValid(valid);

            // In iText 8, getHashAlgorithm() was renamed to getDigestAlgorithmName()
            try {
                details.setAlgorithm(pkcs7.getDigestAlgorithmName());
            } catch (Throwable e) {
                details.setAlgorithm("Unknown");
            }

            X509Certificate cert = pkcs7.getSigningCertificate();
            if (cert != null) {
                details.setSigner(cert.getSubjectX500Principal().getName());
                details.setPublicKeyHash(calculatePublicKeyHash(cert.getPublicKey()));
                details.setPublicKeyMatch(true);
            }

            try {
                // In iText 8, getDigest() might be missing or private.
                // Using reflection to get digestAttr if available, otherwise getEncodedPKCS7
                byte[] hash = null;
                try {
                    java.lang.reflect.Field field = pkcs7.getClass().getDeclaredField("digestAttr");
                    field.setAccessible(true);
                    hash = (byte[]) field.get(pkcs7);
                } catch (Exception e) {
                    // Fallback or skip
                }

                if (hash != null) {
                    details.setHash(Base64.getEncoder().encodeToString(hash));
                } else {
                    details.setHash("Unavailable");
                }
            } catch (Throwable e) {
                details.setHash("Unavailable");
            }

            if (pkcs7.getSignDate() != null) {
                details.setTimestamp(
                        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(pkcs7.getSignDate().getTime()));
            }

        } catch (Exception e) {
            result.setValid(false);
            System.err.println("[ERROR] External PDF verification failed: " + e.getMessage());
        }
        return result;
    }

    private static String calculatePublicKeyHash(PublicKey publicKey) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(publicKey.getEncoded());
        return Base64.getEncoder().encodeToString(hash);
    }
}
