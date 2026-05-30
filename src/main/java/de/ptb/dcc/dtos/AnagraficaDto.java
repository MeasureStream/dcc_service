package de.ptb.dcc.dtos;

import java.time.OffsetDateTime;

/**
 * DTO generico per CalibrationMethod, MeasurestreamCompany, ClientCompany.
 * Il campo jsonData contiene il JSON grezzo.
 */
public class AnagraficaDto {

    private Long id;
    private String name;
    private String jsonData;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getJsonData() { return jsonData; }
    public void setJsonData(String jsonData) { this.jsonData = jsonData; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
