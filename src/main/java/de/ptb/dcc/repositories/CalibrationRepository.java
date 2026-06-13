package de.ptb.dcc.repositories;

import de.ptb.dcc.entities.Calibration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CalibrationRepository extends JpaRepository<Calibration, Long> {
    List<Calibration> findBySub(String sub);
    List<Calibration> findByMuId(Long muId);
    List<Calibration> findByProcessed(boolean processed);
    java.util.Optional<Calibration> findByCalibrationRequestId(Long calibrationRequestId);

    /** Latest successful calibration for the same sensor (mu_id), excluding the current one. */
    java.util.Optional<Calibration> findTopByMuIdAndRunStatusAndIdNotOrderByCreatedAtDesc(
            Long muId, String runStatus, Long excludeId);
}
