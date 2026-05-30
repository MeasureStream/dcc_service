package de.ptb.dcc.repositories;

import de.ptb.dcc.entities.MeasurestreamCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeasurestreamCompanyRepository extends JpaRepository<MeasurestreamCompany, Long> {
    boolean existsByName(String name);
    List<MeasurestreamCompany> findAllByOrderByCreatedAtDesc();
}
