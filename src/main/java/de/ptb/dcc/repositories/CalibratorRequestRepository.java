package de.ptb.dcc.repositories;

import de.ptb.dcc.entities.CalibratorRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CalibratorRequestRepository extends JpaRepository<CalibratorRequest, Long> {

    Optional<CalibratorRequest> findByCalibrationId(String calibrationId);

    List<CalibratorRequest> findByMuId(Long muId);

    List<CalibratorRequest> findByProcessed(boolean processed);
}
