package de.ptb.dcc.repositories;

import de.ptb.dcc.entities.CalibrationMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CalibrationMethodRepository extends JpaRepository<CalibrationMethod, Long> {
    boolean existsByName(String name);
    List<CalibrationMethod> findAllByOrderByCreatedAtDesc();
}
