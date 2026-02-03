package de.ptb.dcc.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "dcc")
public class Dcc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne
    @JoinColumn(name = "mu_id", referencedColumnName = "id")
    @JsonIgnore
    private MeasurementUnit mu;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "pdf_valid", nullable = false)
    private boolean pdfValid = false;

    @Column(name = "xml_valid", nullable = false)
    private boolean xmlValid = false;

    @Column(name = "pdf_url", columnDefinition = "TEXT")
    private String pdfUrl;

    @Column(name = "xml_url", columnDefinition = "TEXT")
    private String xmlUrl;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "dcc_json", columnDefinition = "TEXT", nullable = false)
    private String dccJson;

    @Column(name = "calibration_date")
    private OffsetDateTime calibrationDate;

    @Column(name = "expiration_date")
    private OffsetDateTime expirationDate;

    @Column(name = "hash_xml", columnDefinition = "TEXT")
    private String hashXml;

    @Column(name = "hash_pdf", columnDefinition = "TEXT")
    private String hashPdf;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public MeasurementUnit getMu() { return mu; }
    public void setMu(MeasurementUnit mu) { this.mu = mu; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public boolean isPdfValid() { return pdfValid; }
    public void setPdfValid(boolean pdfValid) { this.pdfValid = pdfValid; }
    public boolean isXmlValid() { return xmlValid; }
    public void setXmlValid(boolean xmlValid) { this.xmlValid = xmlValid; }
    public String getPdfUrl() { return pdfUrl; }
    public void setPdfUrl(String pdfUrl) { this.pdfUrl = pdfUrl; }
    public String getXmlUrl() { return xmlUrl; }
    public void setXmlUrl(String xmlUrl) { this.xmlUrl = xmlUrl; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
    public String getDccJson() { return dccJson; }
    public void setDccJson(String dccJson) { this.dccJson = dccJson; }

    public OffsetDateTime getCalibrationDate() { return calibrationDate; }
    public void setCalibrationDate(OffsetDateTime calibrationDate) { this.calibrationDate = calibrationDate; }

    public OffsetDateTime getExpirationDate() { return expirationDate; }
    public void setExpirationDate(OffsetDateTime expirationDate) { this.expirationDate = expirationDate; }

    public String getHashXml() { return hashXml; }
    public void setHashXml(String hashXml) { this.hashXml = hashXml; }

    public String getHashPdf() { return hashPdf; }
    public void setHashPdf(String hashPdf) { this.hashPdf = hashPdf; }
}
