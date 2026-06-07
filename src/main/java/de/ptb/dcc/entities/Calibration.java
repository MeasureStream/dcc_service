package de.ptb.dcc.entities;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "calibration")
public class Calibration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mu_id", nullable = false)
    private Long muId;

    @Column(name = "processed", nullable = false)
    private boolean processed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "sub", nullable = false)
    private String sub;

    @Column(name = "calibration_data", columnDefinition = "TEXT", nullable = false)
    private String calibrationData;

    @Column(name = "description")
    private String description;

    @Column(name = "calibration_device_id")
    private String calibrationDeviceId;

    /** FK 1:1 verso CalibrationRequest che ha originato questo certificato */
    @Column(name = "calibration_request_id", unique = true)
    private Long calibrationRequestId;

    // ── Wizard JSON fields (uno per step) ─────────────────────────────────

    /** Step 0 — base_input.json (importato da file, modificabile) */
    @Column(name = "base_input_json", columnDefinition = "TEXT")
    private String baseInputJson;

    /** Step 1 — calibration_method selezionato dall'anagrafica (modifiche solo per questa calib) */
    @Column(name = "calibration_method_json", columnDefinition = "TEXT")
    private String calibrationMethodJson;

    /** Step 2 — measurestream_company selezionata dall'anagrafica */
    @Column(name = "measurestream_company_json", columnDefinition = "TEXT")
    private String measurestreamCompanyJson;

    /** Step 3 — client_company selezionata dall'anagrafica */
    @Column(name = "client_company_json", columnDefinition = "TEXT")
    private String clientCompanyJson;

    /** Step 4 — job.json auto-generato da CalibrationRequest, modificabile */
    @Column(name = "job_json", columnDefinition = "TEXT")
    private String jobJson;

    /** Output finale di build_input_json.py (step 5) */
    @Column(name = "certificato_in", columnDefinition = "TEXT")
    private String certificatoIn;

    /** Output della calibrazione — certificato_funzione_filled.json */
    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    /** Lista URL immagini (JSON array di stringhe) */
    @Column(name = "images", columnDefinition = "TEXT")
    private String images;

    // ── Calibration run fields ─────────────────────────────────────────────

    /** ID univoco del run (= calibrationId dalla CalibrationRequest, usato come dir name) */
    @Column(name = "run_id")
    private String runId;

    /** Stato del run: PENDING, RUNNING, SUCCESS, FAILED */
    @Column(name = "run_status", length = 20)
    private String runStatus;

    /** stdout + stderr completo del processo Python */
    @Column(name = "run_log", columnDefinition = "TEXT")
    private String runLog;

    /** Contenuto di conformity.json generato da run_validation */
    @Column(name = "conformity_json", columnDefinition = "TEXT")
    private String conformityJson;

    /** URL locale al PDF generato (es. /api/calibrations/static/runs/<runId>/output/ntc_cert_funzione.pdf) */
    @Column(name = "pdf_output_url", columnDefinition = "TEXT")
    private String pdfOutputUrl;

    /** Contenuto XML del DCC generato (ntc_calibration_certificate.xml) */
    @Column(name = "dcc_xml", columnDefinition = "TEXT")
    private String dccXml;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

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

    public String getCalibrationData() { return calibrationData; }
    public void setCalibrationData(String calibrationData) { this.calibrationData = calibrationData; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCalibrationDeviceId() { return calibrationDeviceId; }
    public void setCalibrationDeviceId(String calibrationDeviceId) { this.calibrationDeviceId = calibrationDeviceId; }

    public Long getCalibrationRequestId() { return calibrationRequestId; }
    public void setCalibrationRequestId(Long calibrationRequestId) { this.calibrationRequestId = calibrationRequestId; }

    public String getBaseInputJson() { return baseInputJson; }
    public void setBaseInputJson(String baseInputJson) { this.baseInputJson = baseInputJson; }

    public String getCalibrationMethodJson() { return calibrationMethodJson; }
    public void setCalibrationMethodJson(String calibrationMethodJson) { this.calibrationMethodJson = calibrationMethodJson; }

    public String getMeasurestreamCompanyJson() { return measurestreamCompanyJson; }
    public void setMeasurestreamCompanyJson(String measurestreamCompanyJson) { this.measurestreamCompanyJson = measurestreamCompanyJson; }

    public String getClientCompanyJson() { return clientCompanyJson; }
    public void setClientCompanyJson(String clientCompanyJson) { this.clientCompanyJson = clientCompanyJson; }

    public String getJobJson() { return jobJson; }
    public void setJobJson(String jobJson) { this.jobJson = jobJson; }

    public String getCertificatoIn() { return certificatoIn; }
    public void setCertificatoIn(String certificatoIn) { this.certificatoIn = certificatoIn; }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }

    public String getImages() { return images; }
    public void setImages(String images) { this.images = images; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getRunStatus() { return runStatus; }
    public void setRunStatus(String runStatus) { this.runStatus = runStatus; }

    public String getRunLog() { return runLog; }
    public void setRunLog(String runLog) { this.runLog = runLog; }

    public String getConformityJson() { return conformityJson; }
    public void setConformityJson(String conformityJson) { this.conformityJson = conformityJson; }

    public String getPdfOutputUrl() { return pdfOutputUrl; }
    public void setPdfOutputUrl(String pdfOutputUrl) { this.pdfOutputUrl = pdfOutputUrl; }

    public String getDccXml() { return dccXml; }
    public void setDccXml(String dccXml) { this.dccXml = dccXml; }
}
