package de.ptb.dcc.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.Set;

@Entity
@Table(name = "app_user")
public class User {

    @Id
    @Column(name = "user_id")
    private String userId;

    private String name;
    private String surname;
    private String email;
    private String role;

    @OneToMany(mappedBy = "user", orphanRemoval = true)
    @JsonIgnore
    private Set<Node> nodes;

    @OneToMany(mappedBy = "user", orphanRemoval = true)
    @JsonIgnore
    private Set<MeasurementUnit> mus;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSurname() { return surname; }
    public void setSurname(String surname) { this.surname = surname; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Set<Node> getNodes() { return nodes; }
    public void setNodes(Set<Node> nodes) { this.nodes = nodes; }
    public Set<MeasurementUnit> getMus() { return mus; }
    public void setMus(Set<MeasurementUnit> mus) { this.mus = mus; }
}
