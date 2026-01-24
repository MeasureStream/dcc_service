package de.ptb.dcc.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "measurement_unit")
public class MeasurementUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private String type;

    @Column(name = "measures_unit", nullable = false)
    private String measuresUnit;

    @Column(name = "network_id", unique = true)
    private Long networkId;

    @ManyToOne
    @JoinColumn(name = "node_id")
    @JsonIgnore
    private Node node;

    @ManyToOne
    @JoinColumn(name = "user_user_id", referencedColumnName = "user_id")
    @JsonIgnore
    private User user;

    @OneToMany(mappedBy = "mu", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private java.util.List<Dcc> dccs;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMeasuresUnit() {
        return measuresUnit;
    }

    public void setMeasuresUnit(String measuresUnit) {
        this.measuresUnit = measuresUnit;
    }

    public Long getNetworkId() {
        return networkId;
    }

    public void setNetworkId(Long networkId) {
        this.networkId = networkId;
    }

    public Node getNode() {
        return node;
    }

    public void setNode(Node node) {
        this.node = node;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public java.util.List<Dcc> getDccs() {
        return dccs;
    }

    public void setDccs(java.util.List<Dcc> dccs) {
        this.dccs = dccs;
    }
}
