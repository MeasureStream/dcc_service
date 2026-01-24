package de.ptb.dcc.repositories;

import de.ptb.dcc.entities.MeasurementUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MeasurementUnitRepository extends JpaRepository<MeasurementUnit, Long> {

    Optional<MeasurementUnit> findByIdAndUser_UserId(Long id, String ownerId);

    List<MeasurementUnit> findAllByNetworkIdAndUser_UserId(Long networkId, String ownerId);
    Page<MeasurementUnit> findAllByNetworkIdAndUser_UserId(Long networkId, String ownerId, Pageable pageable);

    List<MeasurementUnit> findAllByUser_UserId(String ownerId);
    Page<MeasurementUnit> findAllByUser_UserId(String ownerId, Pageable pageable);

    List<MeasurementUnit> findAllByNetworkId(Long networkId);

    Optional<MeasurementUnit> findByNetworkIdAndUser_UserId(Long networkId, String ownerId);
    Optional<MeasurementUnit> findByNetworkId(Long networkId);

    Page<MeasurementUnit> findAllByNetworkId(Long networkId, Pageable pageable);

    List<MeasurementUnit> findByNodeIsNullAndUser_UserId(String userId);

    List<MeasurementUnit> findAllByNodeIsNull();

    @Query("SELECT MAX(m.networkId) FROM MeasurementUnit m")
    Long findMaxNetworkId();
}
