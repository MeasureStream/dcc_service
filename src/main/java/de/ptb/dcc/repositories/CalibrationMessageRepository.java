package de.ptb.dcc.repositories;

import de.ptb.dcc.entities.CalibrationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CalibrationMessageRepository extends JpaRepository<CalibrationMessage, Long> {

    /** Tutti i messaggi di una stessa calibrazione, ordinati per step_index */
    List<CalibrationMessage> findByCalibIdOrderByStepIndexAsc(String calibId);

    /** Messaggi non ancora assemblati per un calib_id */
    List<CalibrationMessage> findByCalibIdAndAssembledFalse(String calibId);

    /** Conta quanti step sono arrivati per un calib_id */
    long countByCalibId(String calibId);

    /** Verifica se esiste già un messaggio per questo calib_id + step_index (idempotenza) */
    boolean existsByCalibIdAndStepIndex(String calibId, Integer stepIndex);
}
