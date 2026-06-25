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

    /**
     * Latest successful calibration for the same sensor (joined via the
     * calibration_request row that carries the sensor_id), excluding the
     * current one. The "skip if no adjustment" walk-back is implemented
     * in the service layer by inspecting {@code calibration_done} in the
     * stored {@code last_calibration_json} (see CalibrationRunService).
     */
    @org.springframework.data.jpa.repository.Query(
        "SELECT c FROM Calibration c " +
        "WHERE c.runStatus = :runStatus " +
        "AND c.id <> :excludeId " +
        "AND c.calibrationRequestId IN " +
        "  (SELECT r.id FROM CalibrationRequest r WHERE r.sensorId = :sensorId) " +
        "ORDER BY c.createdAt DESC")
    java.util.List<Calibration> findBySensorIdAndRunStatusExcluding(
            Long sensorId, String runStatus, Long excludeId);
}
