package de.ptb.dcc.dtos;

/**
 * Request to create a new DCC certificate manually (without a Kafka CalibrationRequest).
 * The backend creates an internal CalibrationRequest, initializes the wizard, and
 * pre-loads all step templates.
 */
public class ManualCertificateRequest {

    /** Human-readable name for the DCC (required) */
    private String name;

    /** Sensor ID to associate with the DCC (optional — leave null for templates) */
    private Long sensorId;

    /** Measurement Unit ID to associate with the CalibrationRequest (optional) */
    private Long muId;

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public Long getSensorId() { return sensorId; }
    public void setSensorId(Long v) { this.sensorId = v; }

    public Long getMuId() { return muId; }
    public void setMuId(Long v) { this.muId = v; }
}
