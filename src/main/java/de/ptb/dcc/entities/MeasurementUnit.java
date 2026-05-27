package de.ptb.dcc.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.List;

/**
 * Entità passiva (sola lettura per dcc_service).
 * Specchio di MeasurementUnit in sensor-manager.
 * extendedId = EUID hardware (corrisponde a MeasurementUnit.extendedId in sensor-manager).
 * user_user_id è nullable: null se la CU parent non è stata ancora reclamata.
 */
@Entity
@Table(name = "measurement_unit")
public class MeasurementUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    // EUID hardware univoco (= extendedId in sensor-manager)
    @Column(name = "extended_id", unique = true)
    private Long extendedId;

    // indirizzo locale sul bus della CU
    private Integer localId;

    // numero modello che determina il set di sensori (es. 1, 100)
    private Integer model;

    @ManyToOne
    @JoinColumn(name = "control_unit_id", nullable = true)
    @JsonIgnore
    private ControlUnit controlUnit;

    @OneToMany(mappedBy = "measurementUnit", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Sensor> sensors;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getExtendedId() { return extendedId; }
    public void setExtendedId(Long extendedId) { this.extendedId = extendedId; }

    public Integer getLocalId() { return localId; }
    public void setLocalId(Integer localId) { this.localId = localId; }

    public Integer getModel() { return model; }
    public void setModel(Integer model) { this.model = model; }

    public ControlUnit getControlUnit() { return controlUnit; }
    public void setControlUnit(ControlUnit controlUnit) { this.controlUnit = controlUnit; }

    public List<Sensor> getSensors() { return sensors; }
    public void setSensors(List<Sensor> sensors) { this.sensors = sensors; }
}
