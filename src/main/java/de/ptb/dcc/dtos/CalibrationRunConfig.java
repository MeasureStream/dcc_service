package de.ptb.dcc.dtos;

/**
 * Input DTO for POST /api/calibrations/wizard/{calibrationId}/run.
 *
 * Fields set by the user in the CalibrationRunModal.
 * Input/output paths are managed by the system; only template file names are user-selectable.
 */
public class CalibrationRunConfig {

    /** File name of the sensor JSON inside models_in/sensors/ (e.g. "ntc_temperature.json") */
    private String sensorJson;

    /** File name of the reference JSON inside models_in/references/ (e.g. "fluke_9142.json") */
    private String refJson;

    /**
     * Calibration procedure override. One of: linear, cubic, cube-log, linear_interp, cubic_interp.
     * If null, the procedure declared in the sensor JSON is used.
     */
    private String procedure;

    /** Whether to generate calibration and conformity charts (default: true) */
    private boolean charts = true;

    /** Verbose stdout output (default: true) */
    private boolean verbose = true;

    /** Skip calibration update when all as-found errors are within limits (default: false) */
    private boolean updateIfOutRange = false;

    /** Enable dimensional unit checks (default: false) */
    private boolean checkUnits = false;

    /** Enable unit conversion in results (default: false) */
    private boolean convertUnits = false;

    /** Skip PDF generation (default: false) */
    private boolean noPdf = false;

    /** Skip DCC XML generation (default: false) */
    private boolean noXml = false;

    // ── Getters / Setters ──────────────────────────────────────────────────

    public String getSensorJson() { return sensorJson; }
    public void setSensorJson(String v) { this.sensorJson = v; }

    public String getRefJson() { return refJson; }
    public void setRefJson(String v) { this.refJson = v; }

    public String getProcedure() { return procedure; }
    public void setProcedure(String v) { this.procedure = v; }

    public boolean isCharts() { return charts; }
    public void setCharts(boolean v) { this.charts = v; }

    public boolean isVerbose() { return verbose; }
    public void setVerbose(boolean v) { this.verbose = v; }

    public boolean isUpdateIfOutRange() { return updateIfOutRange; }
    public void setUpdateIfOutRange(boolean v) { this.updateIfOutRange = v; }

    public boolean isCheckUnits() { return checkUnits; }
    public void setCheckUnits(boolean v) { this.checkUnits = v; }

    public boolean isConvertUnits() { return convertUnits; }
    public void setConvertUnits(boolean v) { this.convertUnits = v; }

    public boolean isNoPdf() { return noPdf; }
    public void setNoPdf(boolean v) { this.noPdf = v; }

    public boolean isNoXml() { return noXml; }
    public void setNoXml(boolean v) { this.noXml = v; }
}
