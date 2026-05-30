package de.ptb.dcc.entities;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Ogni singolo messaggio Kafka ricevuto sul topic "calibrations".
 * N messaggi con lo stesso calib_id formano una calibrazione completa.
 * Struttura messaggio: { calib_id, target, step_index, step_summary,
 *   start_time, start_time_dwell, ref_readings, sensor_b64, sensor_sampling_freq }
 */
@Entity
@Table(name = "calibration_messages")
public class CalibrationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID che raggruppa tutti i messaggi di una stessa calibrazione.
     *  Formato: calib-{calibrator_id}-{mu_id}-{timestamp}
     *  Es: calib-1-1-20260422T175123 */
    @Column(name = "calib_id", nullable = false)
    private String calibId;

    /** Indice dello step (0-based) all'interno della calibrazione */
    @Column(name = "step_index", nullable = false)
    private Integer stepIndex;

    /** Temperatura target di questo step */
    @Column(name = "target")
    private Double target;

    /** Numero totale di step attesi (estratto da step_summary.length) */
    @Column(name = "total_steps")
    private Integer totalSteps;

    /** JSON raw del messaggio completo — tutto salvato per audit */
    @Column(name = "raw_json", columnDefinition = "TEXT", nullable = false)
    private String rawJson;

    /** true quando questo messaggio è stato incluso in una CalibrationRequest */
    @Column(name = "assembled", nullable = false)
    private boolean assembled = false;

    @Column(name = "received_at", nullable = false, updatable = false)
    private OffsetDateTime receivedAt;

    @PrePersist
    protected void onCreate() {
        receivedAt = OffsetDateTime.now();
    }

    // getters / setters

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

    public String getRawJson() { return rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }

    public boolean isAssembled() { return assembled; }
    public void setAssembled(boolean assembled) { this.assembled = assembled; }

    public OffsetDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(OffsetDateTime receivedAt) { this.receivedAt = receivedAt; }
}
