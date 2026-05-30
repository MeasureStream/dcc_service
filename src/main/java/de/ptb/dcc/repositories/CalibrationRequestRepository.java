package de.ptb.dcc.repositories;

import de.ptb.dcc.entities.CalibrationRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CalibrationRequestRepository extends JpaRepository<CalibrationRequest, Long> {

    Optional<CalibrationRequest> findByCalibrationId(String calibrationId);

    boolean existsByCalibrationId(String calibrationId);

    // ── List queries — NO input_json / processed_json ──────────────────────
    // JPQL constructor expression: selects only lightweight fields.

    @Query("SELECT new de.ptb.dcc.entities.CalibrationRequest(r.id, r.calibrationId, r.calibratorId, r.muId, r.sensorId, r.processed, r.createdAt) FROM CalibrationRequest r WHERE r.sensorId = :sensorId ORDER BY r.createdAt DESC")
    List<CalibrationRequest> findAllBySensorIdLight(Long sensorId);

    @Query("SELECT new de.ptb.dcc.entities.CalibrationRequest(r.id, r.calibrationId, r.calibratorId, r.muId, r.sensorId, r.processed, r.createdAt) FROM CalibrationRequest r WHERE r.muId = :muId ORDER BY r.createdAt DESC")
    List<CalibrationRequest> findAllByMuIdLight(Long muId);

    @Query("SELECT new de.ptb.dcc.entities.CalibrationRequest(r.id, r.calibrationId, r.calibratorId, r.muId, r.sensorId, r.processed, r.createdAt) FROM CalibrationRequest r ORDER BY r.createdAt DESC")
    Page<CalibrationRequest> findAllLight(Pageable pageable);

    // ── Full fetch (used only on single-record detail) ─────────────────────
    List<CalibrationRequest> findAllBySensorId(Long sensorId);
    List<CalibrationRequest> findAllByMuId(Long muId);
    Page<CalibrationRequest> findAll(Pageable pageable);
}
