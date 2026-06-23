package de.ptb.dcc.dtos;

public class CalibrationStatusDto {
    private Long id;
    private boolean hasCertificatoIn;
    private String runStatus;
    private boolean hasDccXml;
    private String runId;

    public CalibrationStatusDto() {}

    public CalibrationStatusDto(Long id, boolean hasCertificatoIn, String runStatus, boolean hasDccXml, String runId) {
        this.id = id;
        this.hasCertificatoIn = hasCertificatoIn;
        this.runStatus = runStatus;
        this.hasDccXml = hasDccXml;
        this.runId = runId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public boolean isHasCertificatoIn() { return hasCertificatoIn; }
    public void setHasCertificatoIn(boolean hasCertificatoIn) { this.hasCertificatoIn = hasCertificatoIn; }

    public String getRunStatus() { return runStatus; }
    public void setRunStatus(String runStatus) { this.runStatus = runStatus; }

    public boolean isHasDccXml() { return hasDccXml; }
    public void setHasDccXml(boolean hasDccXml) { this.hasDccXml = hasDccXml; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
}
