package de.ptb.dcc.dtos;

import java.time.OffsetDateTime;

public class CalibrationMessageDto {
    private Long id;
    private String calibId;
    private Integer stepIndex;
    private Double target;
    private Integer totalSteps;
    private boolean assembled;
    private OffsetDateTime receivedAt;
    private String rawJson;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCalibId() { return calibId; }
    public void setCalibId(String calibId) { this.calibId = calibId; }

    public Integer getStepIndex() { return stepIndex; }
    public void setStepIndex(Integer stepIndex) { this.stepIndex = stepIndex; }

    public Double getTarget() { return target; }
    public void setTarget(Double target) { this.target = target; }

    public Integer getTotalSteps() { return totalSteps; }
    public void setTotalSteps(Integer totalSteps) { this.totalSteps = totalSteps; }

    public boolean isAssembled() { return assembled; }
    public void setAssembled(boolean assembled) { this.assembled = assembled; }

    public OffsetDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(OffsetDateTime receivedAt) { this.receivedAt = receivedAt; }

    public String getRawJson() { return rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }
}
