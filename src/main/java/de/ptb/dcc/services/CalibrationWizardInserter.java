package de.ptb.dcc.services;

import de.ptb.dcc.entities.Calibration;
import de.ptb.dcc.repositories.CalibrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bean separato per eseguire l'INSERT della Calibration in una transazione REQUIRES_NEW.
 * Necessario perché Spring AOP non intercetta le chiamate interne allo stesso bean.
 */
@Service
public class CalibrationWizardInserter {

    private static final Logger log = LoggerFactory.getLogger(CalibrationWizardInserter.class);

    private final CalibrationRepository calibrationRepo;

    public CalibrationWizardInserter(CalibrationRepository calibrationRepo) {
        this.calibrationRepo = calibrationRepo;
    }

    /**
     * Tenta l'INSERT in una transazione separata (REQUIRES_NEW).
     * Se arriva DataIntegrityViolationException recupera il record esistente.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Calibration findOrInsert(Long calibrationRequestId, Calibration toInsert) {
        // Double-check dentro la nuova transazione
        return calibrationRepo.findByCalibrationRequestId(calibrationRequestId)
                .orElseGet(() -> {
                    try {
                        Calibration saved = calibrationRepo.saveAndFlush(toInsert);
                        log.info("[Wizard] Created Calibration id={} for CalibrationRequest id={}", saved.getId(), calibrationRequestId);
                        return saved;
                    } catch (DataIntegrityViolationException e) {
                        log.warn("[Wizard] Duplicate key for calibrationRequestId={}, recovering", calibrationRequestId);
                        return calibrationRepo.findByCalibrationRequestId(calibrationRequestId)
                                .orElseThrow(() -> new RuntimeException("Calibration not found after duplicate key"));
                    }
                });
    }
}
