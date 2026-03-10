package de.ptb.dcc.entities;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Persists every raw calibration request received from the
 * 'calibration.request' Kafka topic.
 *
 * Table: calibrator_request
 * Created automatically by Hibernate (ddl-auto=update).
 */
@Entity
@Table(name = "calibrator_request")
public class CalibratorRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique ID produced by the calibrator hardware, e.g. "calib-1-1-2026-02-20T17:54:05". */
    @Column(name = "calibration_id", nullable = false, unique = true)
    private String calibrationId;

    @Column(name = "calibrator_id", nullable = false)
    private Long calibratorId;

    @Column(name = "mu_id", nullable = false)
    private Long muId;

    @Column(name = "sensor_id", nullable = false)
    private Long sensorId;

    /** Full original JSON payload received from Kafka. */
    @Column(name = "input_json", columnDefinition = "TEXT", nullable = false)
    private String inputJson;

    /** False until calibration constants have been computed and sent back. */
    @Column(name = "processed", nullable = false)
    private boolean processed = false;

    /**
     * JSON output written by calibrationDataProcessing() after computation.
     * Null until processed = true.
     */
    @Column(name = "processed_json_output", columnDefinition = "TEXT")
    private String processedJsonOutput;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getCalibrationId() { return calibrationId; }
    public void setCalibrationId(String calibrationId) { this.calibrationId = calibrationId; }

    public Long getCalibratorId() { return calibratorId; }
    public void setCalibratorId(Long calibratorId) { this.calibratorId = calibratorId; }

    public Long getMuId() { return muId; }
    public void setMuId(Long muId) { this.muId = muId; }

    public Long getSensorId() { return sensorId; }
    public void setSensorId(Long sensorId) { this.sensorId = sensorId; }

    public String getInputJson() { return inputJson; }
    public void setInputJson(String inputJson) { this.inputJson = inputJson; }

    public boolean isProcessed() { return processed; }
    public void setProcessed(boolean processed) { this.processed = processed; }

    public String getProcessedJsonOutput() { return processedJsonOutput; }
    public void setProcessedJsonOutput(String processedJsonOutput) { this.processedJsonOutput = processedJsonOutput; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
