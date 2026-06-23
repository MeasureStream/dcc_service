package de.ptb.dcc.repositories;

import de.ptb.dcc.dtos.CalibrationMessageLiteDto;
import de.ptb.dcc.entities.CalibrationMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
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

    // ── Light projections — used for list rendering, excludes raw_json ─────

    @Query("SELECT new de.ptb.dcc.dtos.CalibrationMessageLiteDto(" +
           "  m.id, m.calibId, m.stepIndex, m.target, m.totalSteps, m.assembled, m.receivedAt) " +
           "FROM CalibrationMessage m ORDER BY m.receivedAt DESC, m.id DESC")
    Page<CalibrationMessageLiteDto> findAllLight(Pageable pageable);

    @Query("SELECT new de.ptb.dcc.dtos.CalibrationMessageLiteDto(" +
           "  m.id, m.calibId, m.stepIndex, m.target, m.totalSteps, m.assembled, m.receivedAt) " +
           "FROM CalibrationMessage m WHERE m.calibId = :calibId ORDER BY m.stepIndex ASC, m.id ASC")
    Page<CalibrationMessageLiteDto> findByCalibIdLightPaged(@Param("calibId") String calibId, Pageable pageable);

    /** Keyset "Load more": messaggi più vecchi del cursore, ordinati come la pagina principale */
    @Query("SELECT new de.ptb.dcc.dtos.CalibrationMessageLiteDto(" +
           "  m.id, m.calibId, m.stepIndex, m.target, m.totalSteps, m.assembled, m.receivedAt) " +
           "FROM CalibrationMessage m WHERE m.receivedAt < :before " +
           "ORDER BY m.receivedAt DESC, m.id DESC")
    List<CalibrationMessageLiteDto> findAllBeforeLight(@Param("before") OffsetDateTime before, Pageable pageable);

    @Query("SELECT new de.ptb.dcc.dtos.CalibrationMessageLiteDto(" +
           "  m.id, m.calibId, m.stepIndex, m.target, m.totalSteps, m.assembled, m.receivedAt) " +
           "FROM CalibrationMessage m WHERE m.calibId = :calibId AND m.stepIndex > :afterStep " +
           "ORDER BY m.stepIndex ASC, m.id ASC")
    List<CalibrationMessageLiteDto> findByCalibIdAfterStepLight(@Param("calibId") String calibId,
                                                                @Param("afterStep") int afterStep,
                                                                Pageable pageable);
}
