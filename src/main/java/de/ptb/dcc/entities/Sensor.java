package de.ptb.dcc.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.List;

/**
 * Entità passiva (sola lettura per dcc_service).
 * Specchio di Sensor in sensor-manager.
 * Il DCC è linkato al Sensor tramite sensor_id.
 * L'ownership è ricavata risalendo: Sensor -> MeasurementUnit -> ControlUnit -> User.
 */
@Entity
@Table(name = "sensor")
public class Sensor {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    // chiave logica → nome del template JSON (es. "ntc_temperature")
    private String modelName;

    private Integer sensorIndex;

    // coefficienti di calibrazione
    private Double coeffA;
    private Double coeffB;
    private Double coeffC;
    private Double coeffD;
    private Long calDate;

    @Column(length = 250)
    private String calInitials;

    @ManyToOne
    @JoinColumn(name = "mu_id", nullable = true)
    @JsonIgnore
    private MeasurementUnit measurementUnit;

    @OneToMany(mappedBy = "sensor", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Dcc> dccs;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public Integer getSensorIndex() { return sensorIndex; }
    public void setSensorIndex(Integer sensorIndex) { this.sensorIndex = sensorIndex; }

    public Double getCoeffA() { return coeffA; }
    public void setCoeffA(Double coeffA) { this.coeffA = coeffA; }

    public Double getCoeffB() { return coeffB; }
    public void setCoeffB(Double coeffB) { this.coeffB = coeffB; }

    public Double getCoeffC() { return coeffC; }
    public void setCoeffC(Double coeffC) { this.coeffC = coeffC; }

    public Double getCoeffD() { return coeffD; }
    public void setCoeffD(Double coeffD) { this.coeffD = coeffD; }

    public Long getCalDate() { return calDate; }
    public void setCalDate(Long calDate) { this.calDate = calDate; }

    public String getCalInitials() { return calInitials; }
    public void setCalInitials(String calInitials) { this.calInitials = calInitials; }

    public MeasurementUnit getMeasurementUnit() { return measurementUnit; }
    public void setMeasurementUnit(MeasurementUnit measurementUnit) { this.measurementUnit = measurementUnit; }

    public List<Dcc> getDccs() { return dccs; }
    public void setDccs(List<Dcc> dccs) { this.dccs = dccs; }

    /**
     * Risale la catena Sensor -> MU -> CU -> User per ottenere il proprietario.
     * Restituisce null se la CU non è ancora stata reclamata.
     */
    public User getOwner() {
        if (measurementUnit == null) return null;
        ControlUnit cu = measurementUnit.getControlUnit();
        if (cu == null) return null;
        return cu.getUser();
    }
}
