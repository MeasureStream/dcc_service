package de.ptb.dcc.services;

import de.ptb.dcc.entities.Dcc;
import de.ptb.dcc.entities.MeasurementUnit;
import de.ptb.dcc.repositories.DccRepository;
import de.ptb.dcc.repositories.MeasurementUnitRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DccService {

    private final DccRepository dccRepository;
    private final MeasurementUnitRepository muRepository;

    public DccService(DccRepository dccRepository, MeasurementUnitRepository muRepository) {
        this.dccRepository = dccRepository;
        this.muRepository = muRepository;
    }

    public Page<Dcc> listDccs(String muId, Boolean template, OffsetDateTime createdFrom, OffsetDateTime createdTo, 
                             String orderBy, String orderDir, int limit, int offset) {
        
        Sort sort = Sort.by(Sort.Direction.fromString(orderDir), orderBy);
        Pageable pageable = PageRequest.of(offset / limit, limit, sort);

        Specification<Dcc> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (muId != null && !muId.isEmpty()) {
                try {
                    Long id = Long.parseLong(muId);
                    predicates.add(cb.equal(root.get("mu").get("id"), id));
                } catch (NumberFormatException e) {
                }
            }

            if (template != null && template) {
                predicates.add(cb.isNull(root.get("mu")));
            } else if (template != null && !template) {
                predicates.add(cb.isNotNull(root.get("mu")));
            }

            if (createdFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }

            if (createdTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return dccRepository.findAll(spec, pageable);
    }

    public List<MeasurementUnit> listMus(String userId, boolean all) {
        if (all) {
            return muRepository.findAll();
        } else {
            return muRepository.findAllByUser_UserId(userId);
        }
    }

    public List<MeasurementUnit> listPublicMus(String userId, boolean all) {
        List<MeasurementUnit> baseMus = all ? muRepository.findAll() : muRepository.findAllByUser_UserId(userId);
        return baseMus.stream()
                .filter(mu -> dccRepository.existsByMuAndPublishedAtIsNotNull(mu))
                .collect(Collectors.toList());
    }

    public Optional<Dcc> getPublishedDccByMuId(Long muId) {
        return muRepository.findById(muId)
                .flatMap(mu -> dccRepository.findByMuAndPublishedAtIsNotNull(mu).stream().findFirst());
    }

    @Transactional
    public Dcc createDcc(String muId, String name, String createdBy, String dccJson) {
        Dcc dcc = new Dcc();
        dcc.setName(name);
        dcc.setCreatedBy(createdBy);
        dcc.setDccJson(dccJson != null ? dccJson : "{}");

        if (muId != null && !muId.isEmpty()) {
            try {
                Long id = Long.parseLong(muId);
                muRepository.findById(id).ifPresent(dcc::setMu);
            } catch (NumberFormatException e) {
            }
        }

        return dccRepository.save(dcc);
    }

    public Optional<Dcc> getDcc(Long dccId) {
        return dccRepository.findById(dccId);
    }

    @Transactional
    public Optional<Dcc> updateDcc(Long dccId, String name, String dccJson) {
        return dccRepository.findById(dccId).map(dcc -> {
            if (name != null) dcc.setName(name);
            if (dccJson != null) dcc.setDccJson(dccJson);
            return dccRepository.save(dcc);
        });
    }

    @Transactional
    public Dcc validateDcc(Long dccId, String fileType) {
        return dccRepository.findById(dccId).map(dcc -> {
            if ("PDF".equalsIgnoreCase(fileType)) {
                dcc.setPdfValid(true);
            } else if ("XML".equalsIgnoreCase(fileType)) {
                dcc.setXmlValid(true);
            }
            return dccRepository.save(dcc);
        }).orElseThrow(() -> new RuntimeException("DCC not found"));
    }

    @Transactional
    public Dcc updateDccJson(Long dccId, String dccJson) {
        return dccRepository.findById(dccId).map(dcc -> {
            dcc.setDccJson(dccJson);
            return dccRepository.save(dcc);
        }).orElseThrow(() -> new RuntimeException("DCC not found"));
    }

    @Transactional
    public Dcc publishDcc(Long dccId) {
        return dccRepository.findById(dccId).map(dcc -> {
            dcc.setPublishedAt(OffsetDateTime.now());
            
            if (dcc.getMu() != null) {
                List<Dcc> previousDccs = dccRepository.findByMuAndPublishedAtIsNotNull(dcc.getMu());
                for (Dcc prev : previousDccs) {
                    if (!prev.getId().equals(dcc.getId())) {
                        prev.setPublishedAt(null);
                        dccRepository.save(prev);
                    }
                }
            }
            
            return dccRepository.save(dcc);
        }).orElseThrow(() -> new RuntimeException("DCC not found"));
    }

    @Transactional
    public void deleteDcc(Long dccId) {
        dccRepository.deleteById(dccId);
    }
}
