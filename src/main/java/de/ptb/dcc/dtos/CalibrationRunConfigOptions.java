package de.ptb.dcc.dtos;

import java.util.List;

/**
 * Response DTO for GET /api/calibrations/wizard/{calibrationId}/run-config.
 * Tells the frontend which sensor and reference template files are available,
 * and which procedure options exist.
 */
public class CalibrationRunConfigOptions {

    /** File names available in models_in/sensors/ */
    private List<String> availableSensors;

    /** File names available in models_in/references/ */
    private List<String> availableRefs;

    /** Supported procedure values */
    private List<String> procedures = List.of(
            "linear", "cubic", "cube-log", "linear_interp", "cubic_interp"
    );

    /** The run ID that will be used for this calibration's output directory */
    private String runId;

    /** Whether a previous run result already exists */
    private boolean hasExistingRun;

    public List<String> getAvailableSensors() { return availableSensors; }
    public void setAvailableSensors(List<String> v) { this.availableSensors = v; }

    public List<String> getAvailableRefs() { return availableRefs; }
    public void setAvailableRefs(List<String> v) { this.availableRefs = v; }

    public List<String> getProcedures() { return procedures; }
    public void setProcedures(List<String> v) { this.procedures = v; }

    public String getRunId() { return runId; }
    public void setRunId(String v) { this.runId = v; }

    public boolean isHasExistingRun() { return hasExistingRun; }
    public void setHasExistingRun(boolean v) { this.hasExistingRun = v; }
}
