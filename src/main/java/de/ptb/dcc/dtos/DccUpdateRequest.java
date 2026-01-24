package de.ptb.dcc.dtos;

public class DccUpdateRequest {
    private String name;
    private String dccJson;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDccJson() { return dccJson; }
    public void setDccJson(String dccJson) { this.dccJson = dccJson; }
}
