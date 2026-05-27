package de.ptb.dcc.repositories;

import de.ptb.dcc.entities.Dcc;
import de.ptb.dcc.entities.Sensor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DccRepository extends JpaRepository<Dcc, Long>, JpaSpecificationExecutor<Dcc> {

    List<Dcc> findBySensorAndPublishedAtIsNotNull(Sensor sensor);

    boolean existsBySensorAndPublishedAtIsNotNull(Sensor sensor);

    Optional<Dcc> findByIdAndUser_UserId(Long id, String userId);

    Optional<Dcc> findBySensor_IdAndPublishedAtIsNotNull(Long sensorId);

    List<Dcc> findByHashXml(String hashXml);

    List<Dcc> findByHashPdf(String hashPdf);
}
