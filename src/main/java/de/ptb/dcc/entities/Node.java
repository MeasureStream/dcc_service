package de.ptb.dcc.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.Set;

@Entity
@Table(name = "node")
public class Node {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean standard = false;

    @ManyToOne
    @JoinColumn(name = "user_user_id", referencedColumnName = "user_id")
    @JsonIgnore
    private User user;

    // For DCC service, we mainly need the relationship to measurement units
    @OneToMany(mappedBy = "node", orphanRemoval = true)
    @JsonIgnore
    private Set<MeasurementUnit> measurementUnits;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isStandard() {
        return standard;
    }

    public void setStandard(boolean standard) {
        this.standard = standard;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Set<MeasurementUnit> getMeasurementUnits() {
        return measurementUnits;
    }

    public void setMeasurementUnits(Set<MeasurementUnit> measurementUnits) {
        this.measurementUnits = measurementUnits;
    }
}
