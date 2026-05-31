package de.ptb.dcc.dtos;

import java.util.List;

/**
 * Response DTO for POST /api/dcc/external/verify-conformity.
 *
 * Contains the captured Python stdout/stderr log, overall pass/fail verdict,
 * and a list of generated chart images encoded as Base64 PNG data URIs.
 */
public class ConformityVerificationResultDto {

    private boolean success;        // true if the Python process exited with code 0
    private String  overall;        // "CONFORME" | "NON CONFORME" | "ERROR"
    private String  log;            // full captured stdout+stderr from the script
    private List<ConformityImageDto> images;

    // ── Constructors ──────────────────────────────────────────────────────

    public ConformityVerificationResultDto() {}

    public ConformityVerificationResultDto(boolean success, String overall, String log,
                                           List<ConformityImageDto> images) {
        this.success = success;
        this.overall = overall;
        this.log     = log;
        this.images  = images;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    public boolean isSuccess()   { return success; }
    public void setSuccess(boolean s) { this.success = s; }

    public String getOverall()  { return overall; }
    public void setOverall(String o) { this.overall = o; }

    public String getLog()      { return log; }
    public void setLog(String l) { this.log = l; }

    public List<ConformityImageDto> getImages() { return images; }
    public void setImages(List<ConformityImageDto> i) { this.images = i; }

    // ── Inner DTO ─────────────────────────────────────────────────────────

    public static class ConformityImageDto {
        private String filename;
        private String dataUri;   // "data:image/png;base64,<base64>"

        public ConformityImageDto() {}
        public ConformityImageDto(String filename, String dataUri) {
            this.filename = filename;
            this.dataUri  = dataUri;
        }

        public String getFilename() { return filename; }
        public void setFilename(String f) { this.filename = f; }

        public String getDataUri() { return dataUri; }
        public void setDataUri(String d) { this.dataUri = d; }
    }
}
