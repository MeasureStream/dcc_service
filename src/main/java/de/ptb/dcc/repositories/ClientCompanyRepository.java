package de.ptb.dcc.repositories;

import de.ptb.dcc.entities.ClientCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClientCompanyRepository extends JpaRepository<ClientCompany, Long> {
    boolean existsByName(String name);
    List<ClientCompany> findAllByOrderByCreatedAtDesc();
}
