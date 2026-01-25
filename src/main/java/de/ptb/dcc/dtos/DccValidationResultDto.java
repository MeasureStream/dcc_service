package de.ptb.dcc.dtos;

import java.util.List;

public class DccValidationResultDto {
    private boolean valid;
    private SignatureDetailsDto signatureDetails;
    private List<DccDto> matchingDccs;

    // Getters and Setters
    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public SignatureDetailsDto getSignatureDetails() { return signatureDetails; }
    public void setSignatureDetails(SignatureDetailsDto signatureDetails) { this.signatureDetails = signatureDetails; }

    public List<DccDto> getMatchingDccs() { return matchingDccs; }
    public void setMatchingDccs(List<DccDto> matchingDccs) { this.matchingDccs = matchingDccs; }

    public static class SignatureDetailsDto {
        private String algorithm;
        private String signer;
        private boolean publicKeyMatch;
        private String timestamp;
        private String hash;
        private String publicKeyHash;

        // Getters and Setters
        public String getAlgorithm() { return algorithm; }
        public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

        public String getSigner() { return signer; }
        public void setSigner(String signer) { this.signer = signer; }

        public boolean isPublicKeyMatch() { return publicKeyMatch; }
        public void setPublicKeyMatch(boolean publicKeyMatch) { this.publicKeyMatch = publicKeyMatch; }

        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

        public String getHash() { return hash; }
        public void setHash(String hash) { this.hash = hash; }

        public String getPublicKeyHash() { return publicKeyHash; }
        public void setPublicKeyHash(String publicKeyHash) { this.publicKeyHash = publicKeyHash; }
    }
}
