package de.ptb.dcc.dtos;

import java.time.OffsetDateTime;

public class CalibrationRequestDto {
    private Long id;
    private String calibrationId;
    private Long calibratorId;
    private Long muId;
    private Long sensorId;
    private boolean processed;
    private OffsetDateTime createdAt;
    // JSON raw completo — visibile in frontend
    private String inputJson;
    private String processedJson;

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

    public boolean isProcessed() { return processed; }
    public void setProcessed(boolean processed) { this.processed = processed; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public String getInputJson() { return inputJson; }
    public void setInputJson(String inputJson) { this.inputJson = inputJson; }

    public String getProcessedJson() { return processedJson; }
    public void setProcessedJson(String processedJson) { this.processedJson = processedJson; }
}
