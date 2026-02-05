package de.ptb.dcc.dtos;

import java.time.OffsetDateTime;

public class DccDto {
    private Long id;
    private String muId;
    private String name;
    private String createdBy;
    private String createdByName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String status;
    private boolean pdfValid;
    private boolean xmlValid;
    private String pdfUrl;
    private String xmlUrl;
    private String dccJson;
    private OffsetDateTime publishedAt;
    private OffsetDateTime calibrationDate;
    private OffsetDateTime expirationDate;
    private String hashXml;
    private String hashPdf;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMuId() {
        return muId;
    }

    public void setMuId(String muId) {
        this.muId = muId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isPdfValid() {
        return pdfValid;
    }

    public void setPdfValid(boolean pdfValid) {
        this.pdfValid = pdfValid;
    }

    public boolean isXmlValid() {
        return xmlValid;
    }

    public void setXmlValid(boolean xmlValid) {
        this.xmlValid = xmlValid;
    }

    public String getPdfUrl() {
        return pdfUrl;
    }

    public void setPdfUrl(String pdfUrl) {
        this.pdfUrl = pdfUrl;
    }

    public String getXmlUrl() {
        return xmlUrl;
    }

    public void setXmlUrl(String xmlUrl) {
        this.xmlUrl = xmlUrl;
    }

    public String getDccJson() {
        return dccJson;
    }

    public void setDccJson(String dccJson) {
        this.dccJson = dccJson;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public OffsetDateTime getCalibrationDate() {
        return calibrationDate;
    }

    public void setCalibrationDate(OffsetDateTime calibrationDate) {
        this.calibrationDate = calibrationDate;
    }

    public OffsetDateTime getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(OffsetDateTime expirationDate) {
        this.expirationDate = expirationDate;
    }

    public String getHashXml() {
        return hashXml;
    }

    public void setHashXml(String hashXml) {
        this.hashXml = hashXml;
    }

    public String getHashPdf() {
        return hashPdf;
    }

    public void setHashPdf(String hashPdf) {
        this.hashPdf = hashPdf;
    }
}
