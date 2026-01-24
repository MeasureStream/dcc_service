package de.ptb.dcc.dtos;

public class MeasurementUnitDto {
    private Long id;
    private String type;
    private String measuresUnit;
    private Long networkId;
    private Long nodeId;
    private String ownerId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getMeasuresUnit() { return measuresUnit; }
    public void setMeasuresUnit(String measuresUnit) { this.measuresUnit = measuresUnit; }
    public Long getNetworkId() { return networkId; }
    public void setNetworkId(Long networkId) { this.networkId = networkId; }
    public Long getNodeId() { return nodeId; }
    public void setNodeId(Long nodeId) { this.nodeId = nodeId; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
}
