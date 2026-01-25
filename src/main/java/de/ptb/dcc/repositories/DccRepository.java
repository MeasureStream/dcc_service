package de.ptb.dcc.repositories;

import de.ptb.dcc.entities.Dcc;
import de.ptb.dcc.entities.MeasurementUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DccRepository extends JpaRepository<Dcc, Long>, JpaSpecificationExecutor<Dcc> {
    List<Dcc> findByMuAndPublishedAtIsNotNull(MeasurementUnit mu);

    boolean existsByMuAndPublishedAtIsNotNull(MeasurementUnit mu);

    List<Dcc> findByHashXml(String hashXml);

    List<Dcc> findByHashPdf(String hashPdf);
}
