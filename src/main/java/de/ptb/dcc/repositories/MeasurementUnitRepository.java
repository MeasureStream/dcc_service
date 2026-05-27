package de.ptb.dcc.repositories;

import de.ptb.dcc.entities.MeasurementUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MeasurementUnitRepository extends JpaRepository<MeasurementUnit, Long> {

    Optional<MeasurementUnit> findByExtendedId(Long extendedId);

    // MU di una specifica CU
    List<MeasurementUnit> findAllByControlUnit_Id(Long controlUnitId);

    // MU di una CU per devEui
    List<MeasurementUnit> findAllByControlUnit_DevEui(Long devEui);

    // MU raggiungibili dall'utente: risale MU -> CU -> User
    List<MeasurementUnit> findAllByControlUnit_User_UserId(String userId);
}
