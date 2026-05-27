package de.ptb.dcc.repositories;

import de.ptb.dcc.entities.Sensor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SensorRepository extends JpaRepository<Sensor, Long> {

    // Sensori di una specifica MU
    List<Sensor> findAllByMeasurementUnit_Id(Long muId);

    // Sensori raggiungibili dall'utente: risale Sensor -> MU -> CU -> User
    List<Sensor> findAllByMeasurementUnit_ControlUnit_User_UserId(String userId);

    // Lookup singolo sensor-scoped all'utente
    @Query("SELECT s FROM Sensor s WHERE s.id = :id AND s.measurementUnit.controlUnit.user.userId = :userId")
    Optional<Sensor> findByIdAndOwnerUserId(Long id, String userId);

    // Sensori di una CU (tramite devEui)
    List<Sensor> findAllByMeasurementUnit_ControlUnit_DevEui(Long devEui);
}
