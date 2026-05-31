package de.ptb.dcc.dtos;

public class DccCreateRequest {
    private String sensorId;
    private String name;
    private String dccJson;
    private Long calibrationRequestId;

    public String getSensorId() { return sensorId; }
    public void setSensorId(String sensorId) { this.sensorId = sensorId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDccJson() { return dccJson; }
    public void setDccJson(String dccJson) { this.dccJson = dccJson; }

    public Long getCalibrationRequestId() { return calibrationRequestId; }
    public void setCalibrationRequestId(Long calibrationRequestId) { this.calibrationRequestId = calibrationRequestId; }
}
