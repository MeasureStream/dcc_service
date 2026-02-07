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

    @Column(name = "sub", nullable = false)
    private String sub;

    @Column(name = "calibration_data", columnDefinition = "TEXT", nullable = false)
    private String calibrationData;

    @Column(name = "description")
    private String description;

    @Column(name = "calibration_device_id")
    private String calibrationDeviceId;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMuId() {
        return muId;
    }

    public void setMuId(Long muId) {
        this.muId = muId;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getSub() {
        return sub;
    }

    public void setSub(String sub) {
        this.sub = sub;
    }

    public String getCalibrationData() {
        return calibrationData;
    }

    public void setCalibrationData(String calibrationData) {
        this.calibrationData = calibrationData;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCalibrationDeviceId() {
        return calibrationDeviceId;
    }

    public void setCalibrationDeviceId(String calibrationDeviceId) {
        this.calibrationDeviceId = calibrationDeviceId;
    }
}
