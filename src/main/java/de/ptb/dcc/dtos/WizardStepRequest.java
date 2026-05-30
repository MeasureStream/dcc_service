package de.ptb.dcc.dtos;

/**
 * Request generica per salvare il JSON di un singolo step del wizard.
 * Il campo step indica quale step (0-4) viene salvato.
 * Il campo jsonData contiene il JSON modificato dall'utente.
 */
public class WizardStepRequest {

    /** 0=base_input, 1=calibration_method, 2=measurestream_company, 3=client_company, 4=job */
    private int step;
    private String jsonData;

    public int getStep() { return step; }
    public void setStep(int step) { this.step = step; }

    public String getJsonData() { return jsonData; }
    public void setJsonData(String jsonData) { this.jsonData = jsonData; }
}
