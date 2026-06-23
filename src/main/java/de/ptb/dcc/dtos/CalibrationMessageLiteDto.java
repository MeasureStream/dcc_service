package de.ptb.dcc.dtos;

import java.time.OffsetDateTime;

public class CalibrationMessageLiteDto {
    private Long id;
    private String calibId;
    private Integer stepIndex;
    private Double target;
    private Integer totalSteps;
    private boolean assembled;
    private OffsetDateTime receivedAt;

    public CalibrationMessageLiteDto() {}

    public CalibrationMessageLiteDto(Long id, String calibId, Integer stepIndex, Double target,
                                     Integer totalSteps, boolean assembled, OffsetDateTime receivedAt) {
        this.id = id;
        this.calibId = calibId;
        this.stepIndex = stepIndex;
        this.target = target;
        this.totalSteps = totalSteps;
        this.assembled = assembled;
        this.receivedAt = receivedAt;
    }

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
}
