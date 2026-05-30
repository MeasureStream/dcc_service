package de.ptb.dcc.services;

import de.ptb.dcc.entities.CalibrationMethod;
import de.ptb.dcc.entities.ClientCompany;
import de.ptb.dcc.entities.MeasurestreamCompany;
import de.ptb.dcc.repositories.CalibrationMethodRepository;
import de.ptb.dcc.repositories.ClientCompanyRepository;
import de.ptb.dcc.repositories.MeasurestreamCompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Inserisce i record di esempio al primo avvio se le tabelle sono vuote.
 * I JSON sono caricati da src/main/resources/calibration_templates/.
 */
@Component
public class AnagraficaSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AnagraficaSeeder.class);

    private final CalibrationMethodRepository methodRepo;
    private final MeasurestreamCompanyRepository msRepo;
    private final ClientCompanyRepository clientRepo;

    public AnagraficaSeeder(CalibrationMethodRepository methodRepo,
                             MeasurestreamCompanyRepository msRepo,
                             ClientCompanyRepository clientRepo) {
        this.methodRepo = methodRepo;
        this.msRepo = msRepo;
        this.clientRepo = clientRepo;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedMethod();
        seedMsCompany();
        seedClientCompany();
    }

    private void seedMethod() {
        if (!methodRepo.findAllByOrderByCreatedAtDesc().isEmpty()) {
            log.info("[Seed] calibration_method already seeded — skipping");
            return;
        }
        try {
            String json = loadResource("calibration_templates/calibration_method.json");
            CalibrationMethod m = new CalibrationMethod();
            m.setName("NTC Temperature PRO-CAL-MST-003");
            m.setJsonData(json);
            methodRepo.save(m);
            log.info("[Seed] calibration_method seeded OK");
        } catch (IOException e) {
            log.error("[Seed] Failed to seed calibration_method: {}", e.getMessage());
        }
    }

    private void seedMsCompany() {
        if (!msRepo.findAllByOrderByCreatedAtDesc().isEmpty()) {
            log.info("[Seed] measurestream_company already seeded — skipping");
            return;
        }
        try {
            String json = loadResource("calibration_templates/measurestream_company.json");
            MeasurestreamCompany ms = new MeasurestreamCompany();
            ms.setName("Measurestream S.r.l. — LAT 042");
            ms.setJsonData(json);
            msRepo.save(ms);
            log.info("[Seed] measurestream_company seeded OK");
        } catch (IOException e) {
            log.error("[Seed] Failed to seed measurestream_company: {}", e.getMessage());
        }
    }

    private void seedClientCompany() {
        if (!clientRepo.findAllByOrderByCreatedAtDesc().isEmpty()) {
            log.info("[Seed] client_company already seeded — skipping");
            return;
        }
        try {
            String json = loadResource("calibration_templates/client_company.json");
            ClientCompany cc = new ClientCompany();
            cc.setName("ThermoTech Industries S.p.A.");
            cc.setJsonData(json);
            clientRepo.save(cc);
            log.info("[Seed] client_company seeded OK");
        } catch (IOException e) {
            log.error("[Seed] Failed to seed client_company: {}", e.getMessage());
        }
    }

    private String loadResource(String path) throws IOException {
        return new ClassPathResource(path)
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
