package de.ptb.dcc.dtos;

import java.time.OffsetDateTime;

public class DccUpdateRequest {
    private String name;
    private String createdBy;
    private String muId;
    private OffsetDateTime calibrationDate;
    private OffsetDateTime expirationDate;
    private String dccJson;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getMuId() { return muId; }
    public void setMuId(String muId) { this.muId = muId; }
    public OffsetDateTime getCalibrationDate() { return calibrationDate; }
    public void setCalibrationDate(OffsetDateTime calibrationDate) { this.calibrationDate = calibrationDate; }
    public OffsetDateTime getExpirationDate() { return expirationDate; }
    public void setExpirationDate(OffsetDateTime expirationDate) { this.expirationDate = expirationDate; }
    public String getDccJson() { return dccJson; }
    public void setDccJson(String dccJson) { this.dccJson = dccJson; }
}
