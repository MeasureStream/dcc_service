package de.ptb.dcc.repositories;

import de.ptb.dcc.entities.ControlUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ControlUnitRepository extends JpaRepository<ControlUnit, Long> {

    Optional<ControlUnit> findByDevEui(Long devEui);

    // Admin: tutte le CU incluse quelle con user_user_id = null
    // (findAll() già incluso da JpaRepository)

    // Utente normale: solo le proprie
    List<ControlUnit> findAllByUser_UserId(String userId);
}
