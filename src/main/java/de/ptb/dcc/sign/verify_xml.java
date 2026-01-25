import org.apache.xml.security.Init;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.utils.Constants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Set;
import java.security.Security;
import java.security.cert.X509Certificate;

/**
 * XML Signature Verification Tool for DCC Documents
 *
 * This class validates the XAdES digital signature and prints diagnostic details.
 */
public class verify_xml {
    static {
        Init.init();
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    public static void main(String[] args) {
        System.out.println("=== XML Signature Verification ===\n");

        try {
            String baseDir = System.getProperty("user.dir");
            File file = new File(baseDir + "/output/dcc_signed.xml");
            if (args.length > 0) {
                file = new File(args[0]);
            }

            if (!file.exists()) {
                System.err.println("[ERROR] File not found: " + file.getAbsolutePath());
                return;
            }

            System.out.println("Checking file: " + file.getAbsolutePath());

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().parse(new FileInputStream(file));

            // Mark all 'Id' attributes for resolution
            NodeList nodes = doc.getElementsByTagName("*");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                if (el.hasAttribute("Id")) {
                    el.setIdAttribute("Id", true);
                }
                if (el.hasAttribute("id")) {
                    el.setIdAttribute("id", true);
                }
            }

            Element sigElement = (Element) doc.getElementsByTagNameNS(Constants.SignatureSpecNS, "Signature").item(0);
            if (sigElement == null) {
                System.err.println("[ERROR] No ds:Signature element found in the file.");
                return;
            }

            XMLSignature sig = new XMLSignature(sigElement, "");
            X509Certificate cert = sig.getKeyInfo().getX509Certificate();

            if (cert == null) {
                System.err.println("[ERROR] No certificate found in ds:KeyInfo.");
                return;
            }

            System.out.println("\n--- Certificate Details ---");
            System.out.println("Subject: " + cert.getSubjectX500Principal());
            System.out.println("Issuer:  " + cert.getIssuerX500Principal());
            System.out.println("Serial:  " + cert.getSerialNumber());

            System.out.println("\n--- Signature Details ---");
            System.out.println("Method:  " + sig.getSignedInfo().getSignatureMethodURI());
            int refCount = sig.getSignedInfo().getLength();
            System.out.println("References found: " + refCount);

            Set<String> refUris = new HashSet<>();
            for (int i = 0; i < refCount; i++) {
                String uri = sig.getSignedInfo().item(i).getURI();
                refUris.add(uri == null ? "" : uri);
                System.out.println("  Ref [" + i + "] URI: " + uri + " | Type: " + sig.getSignedInfo().item(i).getType());
            }

            // Basic XAdES-BES sanity checks:
            if (refCount < 2) {
                System.out.println("\n[FAILURE] XAdES requires at least 2 references (document + SignedProperties). Found: " + refCount);
            } else {
                boolean hasSignedPropsRef = false;
                for (int i = 0; i < refCount; i++) {
                    String type = sig.getSignedInfo().item(i).getType();
                    if ("http://uri.etsi.org/01903#SignedProperties".equals(type)) {
                        hasSignedPropsRef = true;
                        break;
                    }
                }
                if (!hasSignedPropsRef) {
                    System.out.println("\n[FAILURE] Missing SignedProperties reference (Type=http://uri.etsi.org/01903#SignedProperties).");
                }
            }

            boolean valid = sig.checkSignatureValue(cert);
            if (valid) {
                System.out.println("\n[SUCCESS] XML Digital Signature is VALID.");
            } else {
                System.out.println("\n[FAILURE] XML Digital Signature is INVALID.");
            }

        } catch (Exception e) {
            System.err.println("[ERROR] Verification failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
