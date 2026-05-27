package de.ptb.dcc.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.List;

/**
 * Entità passiva (sola lettura per dcc_service).
 * Specchio di ControlUnit in sensor-manager.
 * user_user_id è nullable: null = dispositivo non ancora reclamato da nessun utente.
 * Gli admin vedono tutte le CU, incluse quelle con user_user_id = null.
 */
@Entity
@Table(name = "control_unit")
public class ControlUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "dev_eui", unique = true, nullable = false)
    private Long devEui;

    private String name;

    @ManyToOne
    @JoinColumn(name = "user_user_id", referencedColumnName = "user_id", nullable = true)
    @JsonIgnore
    private User user;

    @OneToMany(mappedBy = "controlUnit")
    @JsonIgnore
    private List<MeasurementUnit> measurementUnits;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDevEui() { return devEui; }
    public void setDevEui(Long devEui) { this.devEui = devEui; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public List<MeasurementUnit> getMeasurementUnits() { return measurementUnits; }
    public void setMeasurementUnits(List<MeasurementUnit> measurementUnits) { this.measurementUnits = measurementUnits; }
}
