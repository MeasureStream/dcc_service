package de.ptb.dcc.dtos;

public class SensorDto {
    private Long id;
    private String modelName;
    private Integer sensorIndex;
    // extendedId della MU a cui appartiene il sensore
    private Long muExtendedId;
    // devEui della CU a cui appartiene la MU
    private Long cuDevEui;
    // userId del proprietario (null se CU non ancora reclamata)
    private String ownerId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public Integer getSensorIndex() { return sensorIndex; }
    public void setSensorIndex(Integer sensorIndex) { this.sensorIndex = sensorIndex; }

    public Long getMuExtendedId() { return muExtendedId; }
    public void setMuExtendedId(Long muExtendedId) { this.muExtendedId = muExtendedId; }

    public Long getCuDevEui() { return cuDevEui; }
    public void setCuDevEui(Long cuDevEui) { this.cuDevEui = cuDevEui; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
}
