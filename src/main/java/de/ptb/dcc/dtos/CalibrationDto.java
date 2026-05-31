package de.ptb.dcc.dtos;

import java.time.OffsetDateTime;

/**
 * DTO per la entity Calibration (versione estesa con campi wizard).
 */
public class CalibrationDto {

    private Long id;
    private Long muId;
    private boolean processed;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String sub;
    private String description;
    private String calibrationDeviceId;
    private Long calibrationRequestId;

    // wizard steps
    private String baseInputJson;
    private String calibrationMethodJson;
    private String measurestreamCompanyJson;
    private String clientCompanyJson;
    private String jobJson;
    private String certificatoIn;
    private String resultJson;
    private String images;

    // calibration run fields
    private String runId;
    private String runStatus;
    private String runLog;
    private String conformityJson;
    private String pdfOutputUrl;
    private String dccXml;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMuId() { return muId; }
    public void setMuId(Long muId) { this.muId = muId; }

    public boolean isProcessed() { return processed; }
    public void setProcessed(boolean processed) { this.processed = processed; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getSub() { return sub; }
    public void setSub(String sub) { this.sub = sub; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCalibrationDeviceId() { return calibrationDeviceId; }
    public void setCalibrationDeviceId(String v) { this.calibrationDeviceId = v; }

    public Long getCalibrationRequestId() { return calibrationRequestId; }
    public void setCalibrationRequestId(Long v) { this.calibrationRequestId = v; }

    public String getBaseInputJson() { return baseInputJson; }
    public void setBaseInputJson(String v) { this.baseInputJson = v; }

    public String getCalibrationMethodJson() { return calibrationMethodJson; }
    public void setCalibrationMethodJson(String v) { this.calibrationMethodJson = v; }

    public String getMeasurestreamCompanyJson() { return measurestreamCompanyJson; }
    public void setMeasurestreamCompanyJson(String v) { this.measurestreamCompanyJson = v; }

    public String getClientCompanyJson() { return clientCompanyJson; }
    public void setClientCompanyJson(String v) { this.clientCompanyJson = v; }

    public String getJobJson() { return jobJson; }
    public void setJobJson(String v) { this.jobJson = v; }

    public String getCertificatoIn() { return certificatoIn; }
    public void setCertificatoIn(String v) { this.certificatoIn = v; }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String v) { this.resultJson = v; }

    public String getImages() { return images; }
    public void setImages(String v) { this.images = v; }

    public String getRunId() { return runId; }
    public void setRunId(String v) { this.runId = v; }

    public String getRunStatus() { return runStatus; }
    public void setRunStatus(String v) { this.runStatus = v; }

    public String getRunLog() { return runLog; }
    public void setRunLog(String v) { this.runLog = v; }

    public String getConformityJson() { return conformityJson; }
    public void setConformityJson(String v) { this.conformityJson = v; }

    public String getPdfOutputUrl() { return pdfOutputUrl; }
    public void setPdfOutputUrl(String v) { this.pdfOutputUrl = v; }

    public String getDccXml() { return dccXml; }
    public void setDccXml(String v) { this.dccXml = v; }
}
