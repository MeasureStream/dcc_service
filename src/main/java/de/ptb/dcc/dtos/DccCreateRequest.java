package de.ptb.dcc.dtos;

public class DccCreateRequest {
    private String muId;
    private String name;
    private String dccJson;

    public String getMuId() { return muId; }
    public void setMuId(String muId) { this.muId = muId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDccJson() { return dccJson; }
    public void setDccJson(String dccJson) { this.dccJson = dccJson; }
}
