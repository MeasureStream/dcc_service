package de.ptb.dcc.entities;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Richiesta di calibrazione assemblata da N CalibrationMessage.
 * Contiene il JSON raw in ingresso (messaggi raggruppati)
 * e il JSON elaborato nel formato standard calib_20_45_30_40.json
 * con sensor_b64 → uint16 big-endian decodificato.
 */
@Entity
@Table(name = "calibration_request")
public class CalibrationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID univoco della calibrazione (= calib_id dai messaggi Kafka) */
    @Column(name = "calibration_id", unique = true, nullable = false)
    private String calibrationId;

    /** ID del calibratore hardware (estratto da calib_id: calib-{calibratorId}-{muId}-{ts}) */
    @Column(name = "calibrator_id")
    private Long calibratorId;

    /** MU ID (extendedId) estratto da calib_id */
    @Column(name = "mu_id")
    private Long muId;

    /** Sensor ID (dalla tabella sensor, se disponibile) — null se non ancora risolto */
    @Column(name = "sensor_id")
    private Long sensorId;

    /** JSON aggregato dei messaggi raw (array di tutti gli step) */
    @Column(name = "input_json", columnDefinition = "TEXT", nullable = false)
    private String inputJson;

    /** JSON elaborato nel formato standard con uint16 big-endian */
    @Column(name = "processed_json", columnDefinition = "TEXT")
    private String processedJson;

    /** true se il processedJson è stato generato con successo */
    @Column(name = "processed", nullable = false)
    private boolean processed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    /** Constructor for JPQL projections — excludes TEXT JSON fields */
    public CalibrationRequest(Long id, String calibrationId, Long calibratorId,
                               Long muId, Long sensorId, boolean processed,
                               OffsetDateTime createdAt) {
        this.id = id;
        this.calibrationId = calibrationId;
        this.calibratorId = calibratorId;
        this.muId = muId;
        this.sensorId = sensorId;
        this.processed = processed;
        this.createdAt = createdAt;
    }

    /** Required by JPA */
    public CalibrationRequest() {}

    // getters / setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public String getProcessedJson() { return processedJson; }
    public void setProcessedJson(String processedJson) { this.processedJson = processedJson; }

    public boolean isProcessed() { return processed; }
    public void setProcessed(boolean processed) { this.processed = processed; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
