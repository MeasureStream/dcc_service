package de.ptb.dcc.services;

import de.ptb.dcc.dtos.DccUpdateRequest;
import de.ptb.dcc.dtos.DccValidationResultDto;
import de.ptb.dcc.entities.Dcc;
import de.ptb.dcc.entities.Sensor;
import de.ptb.dcc.entities.User;
import de.ptb.dcc.repositories.DccRepository;
import de.ptb.dcc.repositories.SensorRepository;
import de.ptb.dcc.repositories.UserRepository;
import de.ptb.dcc.utils.SigningUtils;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DccService {

    private final DccRepository dccRepository;
    private final SensorRepository sensorRepository;
    private final UserRepository userRepository;
    private final DccSigningService signingService;
    private final S3Service s3Service;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemimeg.backend.url}")
    private String gemimegBackendUrl;

    public DccService(DccRepository dccRepository, SensorRepository sensorRepository,
            UserRepository userRepository, DccSigningService signingService, S3Service s3Service) {
        this.dccRepository = dccRepository;
        this.sensorRepository = sensorRepository;
        this.userRepository = userRepository;
        this.signingService = signingService;
        this.s3Service = s3Service;
    }

    // -------------------------------------------------------------------------
    // LIST DCCs — admin vede tutti, utente normale solo i propri
    // -------------------------------------------------------------------------
    public Page<Dcc> listDccs(String sensorId, Boolean template, OffsetDateTime createdFrom,
            OffsetDateTime createdTo, String orderBy, String orderDir, int limit, int offset) {

        Sort sort = Sort.by(Sort.Direction.fromString(orderDir), orderBy);
        Pageable pageable = PageRequest.of(offset / limit, limit, sort);

        String currentUserId = getCurrentUserId();
        boolean isAdmin = isAdmin();

        Specification<Dcc> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!isAdmin) {
                predicates.add(cb.equal(root.get("user").get("userId"), currentUserId));
            }

            if (sensorId != null && !sensorId.isEmpty()) {
                try {
                    Long id = Long.parseLong(sensorId);
                    predicates.add(cb.equal(root.get("sensor").get("id"), id));
                } catch (NumberFormatException ignored) {
                }
            }

            if (template != null && template) {
                predicates.add(cb.isNull(root.get("sensor")));
            } else if (template != null && !template) {
                predicates.add(cb.isNotNull(root.get("sensor")));
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

    // -------------------------------------------------------------------------
    // LIST SENSORs — admin vede tutti (inclusi quelli con CU unclaimed),
    // utente normale vede solo i sensori delle proprie CU
    // -------------------------------------------------------------------------
    public List<Sensor> listSensors() {
        if (isAdmin()) {
            return sensorRepository.findAll();
        }
        return sensorRepository.findAllByMeasurementUnit_ControlUnit_User_UserId(getCurrentUserId());
    }

    // -------------------------------------------------------------------------
    // LIST PUBLIC SENSORs — sensori con almeno un DCC pubblicato
    // -------------------------------------------------------------------------
    public List<Sensor> listPublicSensors() {
        List<Sensor> base = isAdmin()
                ? sensorRepository.findAll()
                : sensorRepository.findAllByMeasurementUnit_ControlUnit_User_UserId(getCurrentUserId());
        return base.stream()
                .filter(s -> dccRepository.existsBySensorAndPublishedAtIsNotNull(s))
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // GET published DCC by sensorId (pubblico)
    // -------------------------------------------------------------------------
    public Optional<Dcc> getPublishedDccBySensorId(Long sensorId) {
        return dccRepository.findBySensor_IdAndPublishedAtIsNotNull(sensorId);
    }

    // -------------------------------------------------------------------------
    // CREATE DCC
    // -------------------------------------------------------------------------
    @Transactional
    public Dcc createDcc(String sensorId, String name, String dccJson) {
        return createDcc(sensorId, name, dccJson, null);
    }

    @Transactional
    public Dcc createDcc(String sensorId, String name, String dccJson, Long calibrationRequestId) {
        if (!isAdmin()) throw new AccessDeniedException("Only admins can create DCCs");
        Dcc dcc = new Dcc();
        dcc.setName(name);
        dcc.setDccJson(dccJson != null ? dccJson : "{}");
        dcc.setCreatedBy(getCurrentUserId());
        dcc.setUser(getOrCreateCurrentUser());
        dcc.setCalibrationDate(OffsetDateTime.now());

        if (calibrationRequestId != null) {
            dcc.setCalibrationRequestId(calibrationRequestId);
        }

        if (sensorId != null && !sensorId.isEmpty()) {
            try {
                Long id = Long.parseLong(sensorId);
                sensorRepository.findById(id).ifPresent(sensor -> {
                    // NPE-safe: il sensore può appartenere a una CU non ancora reclamata
                    if (!isAdmin()) {
                        User owner = sensor.getOwner();
                        if (owner == null || !owner.getUserId().equals(getCurrentUserId())) {
                            throw new RuntimeException("You don't have access to this Sensor");
                        }
                    }
                    dcc.setSensor(sensor);
                });
            } catch (NumberFormatException ignored) {
            }
        }

        return dccRepository.save(dcc);
    }

    // -------------------------------------------------------------------------
    // GET DCC — admin per id, utente normale solo i propri
    // -------------------------------------------------------------------------
    public Optional<Dcc> getDcc(Long dccId) {
        if (isAdmin()) {
            return dccRepository.findById(dccId);
        }
        return dccRepository.findByIdAndUser_UserId(dccId, getCurrentUserId());
    }

    // -------------------------------------------------------------------------
    // UPDATE DCC
    // -------------------------------------------------------------------------
    @Transactional
    public Optional<Dcc> updateDcc(Long dccId, DccUpdateRequest request) {
        if (!isAdmin()) throw new AccessDeniedException("Only admins can update DCCs");
        Optional<Dcc> existingDcc = dccRepository.findById(dccId);

        return existingDcc.map(dcc -> {
            if (request.getName() != null)
                dcc.setName(request.getName());
            if (request.getDccJson() != null)
                dcc.setDccJson(request.getDccJson());
            if (request.getCalibrationDate() != null)
                dcc.setCalibrationDate(request.getCalibrationDate());
            if (request.getExpirationDate() != null)
                dcc.setExpirationDate(request.getExpirationDate());
            if (request.getSensorId() != null) {
                if (request.getSensorId().isEmpty()) {
                    dcc.setSensor(null);
                } else {
                    try {
                        Long sid = Long.parseLong(request.getSensorId());
                        sensorRepository.findById(sid).ifPresent(sensor -> {
                            if (!isAdmin()) {
                                User owner = sensor.getOwner();
                                if (owner == null || !owner.getUserId().equals(getCurrentUserId())) {
                                    throw new RuntimeException("You don't have access to this Sensor");
                                }
                            }
                            dcc.setSensor(sensor);
                        });
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return dccRepository.save(dcc);
        });
    }

    // -------------------------------------------------------------------------
    // VALIDATE DCC
    // -------------------------------------------------------------------------
    @Transactional
    public Dcc validateDcc(Long dccId, String fileType) {
        if (!isAdmin()) throw new AccessDeniedException("Only admins can validate/sign DCCs");
        System.out.println("=== VALIDATE DCC STARTED ===");

        Dcc dcc = dccRepository.findById(dccId)
                .orElseThrow(() -> new EntityNotFoundException("DCC not found"));

        System.out.println("DCC found: " + dcc.getName());
        System.out.println("  - XML Valid: " + dcc.isXmlValid());
        System.out.println("  - PDF Valid: " + dcc.isPdfValid());

        try {
            System.out.println("Converting JSON to XML/PDF via " + gemimegBackendUrl);
            String xmlContent = convertToXml(dcc.getDccJson());
            byte[] pdfContentBytes = convertToPdf(dcc.getDccJson());

            System.out.println("Signing and verifying...");
            DccSigningService.SigningResult signingResult = signingService.performSigningAndVerification(dcc,
                    xmlContent, pdfContentBytes);

            if (signingResult != null) {
                uploadToS3(dcc, signingResult);
                dcc.setXmlValid(signingResult.xmlValid);
                dcc.setPdfValid(signingResult.pdfValid);
                dcc.setHashXml(signingResult.hashXml);
                dcc.setHashPdf(signingResult.hashPdf);

                if (signingResult.signedXml != null) signingResult.signedXml.delete();
                if (signingResult.signedPdf != null) signingResult.signedPdf.delete();
            } else {
                System.out.println("ERROR: Signing result is null");
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Validation chain failed: " + e.getMessage());
            e.printStackTrace();
        }

        Dcc saved = dccRepository.save(dcc);
        System.out.println("=== VALIDATE DCC COMPLETED ===");
        return saved;
    }

    private void uploadToS3(Dcc dcc, DccSigningService.SigningResult result) {
        if (result.xmlValid && result.signedXml != null) {
            String xmlKey = "dcc-" + dcc.getId() + ".xml";
            String s3Url = s3Service.uploadFile(xmlKey, result.signedXml, "application/xml");
            if (s3Url != null) {
                dcc.setXmlUrl("/api/dcc/s3/" + dcc.getId() + "/xml");
            } else {
                System.err.println("[ERROR] Failed to upload XML for DCC: " + dcc.getId());
            }
        }
        if (result.pdfValid && result.signedPdf != null) {
            String pdfKey = "dcc-" + dcc.getId() + ".pdf";
            String s3Url = s3Service.uploadFile(pdfKey, result.signedPdf, "application/pdf");
            if (s3Url != null) {
                dcc.setPdfUrl("/api/dcc/s3/" + dcc.getId() + "/pdf");
            } else {
                System.err.println("[ERROR] Failed to upload PDF for DCC: " + dcc.getId());
            }
        }
    }

    public byte[] downloadS3File(Long dccId, String type) {
        Dcc dcc = (isAdmin() ? dccRepository.findById(dccId)
                : dccRepository.findByIdAndUser_UserId(dccId, getCurrentUserId()))
                .orElseThrow(() -> new EntityNotFoundException("DCC not found"));
        String key = "dcc-" + dccId + (type.equalsIgnoreCase("xml") ? ".xml" : ".pdf");
        return s3Service.downloadFile(key);
    }

    private String convertToXml(String dccJson) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(dccJson, headers);
        return restTemplate.postForObject(gemimegBackendUrl + "/api/v1/dcc/xsd/dcc/xml", entity, String.class);
    }

    private byte[] convertToPdf(String dccJson) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(dccJson, headers);
        return restTemplate.postForObject(gemimegBackendUrl + "/api/v1/dcc/xsd/dcc/pdf", entity, byte[].class);
    }

    // -------------------------------------------------------------------------
    // UPDATE JSON
    // -------------------------------------------------------------------------
    @Transactional
    public Dcc updateDccJson(Long dccId, String dccJson) {
        if (!isAdmin()) throw new AccessDeniedException("Only admins can update DCC JSON");
        Dcc dcc = dccRepository.findById(dccId)
                .orElseThrow(() -> new EntityNotFoundException("DCC not found"));
        dcc.setDccJson(dccJson);
        return dccRepository.save(dcc);
    }

    // -------------------------------------------------------------------------
    // PUBLISH / UNPUBLISH
    // -------------------------------------------------------------------------
    @Transactional
    public Dcc publishDcc(Long dccId) {
        if (!isAdmin()) throw new AccessDeniedException("Only admins can publish DCCs");
        Dcc dcc = dccRepository.findById(dccId)
                .orElseThrow(() -> new EntityNotFoundException("DCC not found"));

        if (!dcc.isPdfValid() || !dcc.isXmlValid()) {
            throw new IllegalStateException("DCC must be signed before it can be made effective");
        }

        dcc.setPublishedAt(OffsetDateTime.now());
        dcc.setArchived(false);

        // archivia eventuali precedenti DCC pubblicati per lo stesso sensore
        if (dcc.getSensor() != null) {
            List<Dcc> previousDccs = dccRepository.findBySensorAndPublishedAtIsNotNull(dcc.getSensor());
            for (Dcc prev : previousDccs) {
                if (!prev.getId().equals(dcc.getId())) {
                    prev.setPublishedAt(null);
                    prev.setArchived(true);
                    dccRepository.save(prev);
                }
            }
        }

        return dccRepository.save(dcc);
    }

    @Transactional
    public Dcc unpublishDcc(Long dccId) {
        if (!isAdmin()) throw new AccessDeniedException("Only admins can unpublish DCCs");
        Dcc dcc = dccRepository.findById(dccId)
                .orElseThrow(() -> new EntityNotFoundException("DCC not found"));
        dcc.setPublishedAt(null);
        return dccRepository.save(dcc);
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------
    @Transactional
    public void deleteDcc(Long dccId) {
        if (!isAdmin()) throw new AccessDeniedException("Only admins can delete DCCs");
        Dcc dcc = dccRepository.findById(dccId)
                .orElseThrow(() -> new EntityNotFoundException("DCC not found"));
        dccRepository.delete(dcc);
    }

    // -------------------------------------------------------------------------
    // EXTERNAL VALIDATION
    // -------------------------------------------------------------------------
    public DccValidationResultDto validateExternalXml(MultipartFile file) throws IOException {
        File tempFile = File.createTempFile("ext-dcc-", ".xml");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(file.getBytes());
        }
        DccValidationResultDto result = SigningUtils.validateExternalXml(tempFile);
        tempFile.delete();
        if (result.getSignatureDetails() != null && result.getSignatureDetails().getHash() != null) {
            String hash = result.getSignatureDetails().getHash();
            List<Dcc> matches = dccRepository.findByHashXml(hash);
            result.setMatchingDccs(matches.stream().map(this::mapToDto).collect(Collectors.toList()));
        } else {
            result.setMatchingDccs(Collections.emptyList());
        }
        return result;
    }

    public DccValidationResultDto validateExternalPdf(MultipartFile file) throws IOException {
        File tempFile = File.createTempFile("ext-dcc-", ".pdf");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(file.getBytes());
        }
        DccValidationResultDto result = SigningUtils.validateExternalPdf(tempFile);
        tempFile.delete();
        if (result.getSignatureDetails() != null && result.getSignatureDetails().getHash() != null) {
            String hash = result.getSignatureDetails().getHash();
            List<Dcc> matches = dccRepository.findByHashPdf(hash);
            result.setMatchingDccs(matches.stream().map(this::mapToDto).collect(Collectors.toList()));
        } else {
            result.setMatchingDccs(Collections.emptyList());
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // DOWNLOAD
    // -------------------------------------------------------------------------
    public byte[] getSignedXml(Long dccId) throws Exception {
        Dcc dcc = (isAdmin() ? dccRepository.findById(dccId)
                : dccRepository.findByIdAndUser_UserId(dccId, getCurrentUserId()))
                .orElseThrow(() -> new EntityNotFoundException("DCC not found"));
        String xmlContent = convertToXml(dcc.getDccJson());

        File tempXml = File.createTempFile("dcc-download-", ".xml");
        File signedXml = File.createTempFile("dcc-signed-download-", ".xml");
        try {
            Files.writeString(tempXml.toPath(), xmlContent);
            PrivateKey privateKey = SigningUtils.loadPrivateKey();
            X509Certificate cert = SigningUtils.loadCertificate();
            SigningUtils.signXml(tempXml, signedXml, privateKey, cert);
            return Files.readAllBytes(signedXml.toPath());
        } finally {
            tempXml.delete();
            signedXml.delete();
        }
    }

    public byte[] getSignedPdf(Long dccId) throws Exception {
        Dcc dcc = (isAdmin() ? dccRepository.findById(dccId)
                : dccRepository.findByIdAndUser_UserId(dccId, getCurrentUserId()))
                .orElseThrow(() -> new EntityNotFoundException("DCC not found"));
        byte[] pdfContent = convertToPdf(dcc.getDccJson());

        File tempPdf = File.createTempFile("dcc-download-", ".pdf");
        File signedPdf = File.createTempFile("dcc-signed-download-", ".pdf");
        try {
            Files.write(tempPdf.toPath(), pdfContent);
            PrivateKey privateKey = SigningUtils.loadPrivateKey();
            X509Certificate cert = SigningUtils.loadCertificate();
            SigningUtils.signPdf(tempPdf.getAbsolutePath(), signedPdf.getAbsolutePath(), privateKey, cert);
            return Files.readAllBytes(signedPdf.toPath());
        } finally {
            tempPdf.delete();
            signedPdf.delete();
        }
    }

    // -------------------------------------------------------------------------
    // MAPPING
    // -------------------------------------------------------------------------
    public de.ptb.dcc.dtos.DccDto mapToDto(Dcc dcc) {
        de.ptb.dcc.dtos.DccDto dto = new de.ptb.dcc.dtos.DccDto();
        dto.setId(dcc.getId());
        if (dcc.getSensor() != null) {
            dto.setSensorId(dcc.getSensor().getId());
        }
        dto.setName(dcc.getName());
        dto.setCreatedBy(dcc.getCreatedBy());
        if (dcc.getUser() != null) {
            String fullName = dcc.getUser().getName();
            if (dcc.getUser().getSurname() != null) {
                fullName += " " + dcc.getUser().getSurname();
            }
            dto.setCreatedByName(fullName);
        } else {
            dto.setCreatedByName(dcc.getCreatedBy());
        }
        dto.setCreatedAt(dcc.getCreatedAt());
        dto.setUpdatedAt(dcc.getUpdatedAt());
        dto.setPdfValid(dcc.isPdfValid());
        dto.setXmlValid(dcc.isXmlValid());
        dto.setPdfUrl(dcc.getPdfUrl());
        dto.setXmlUrl(dcc.getXmlUrl());
        dto.setDccJson(dcc.getDccJson());
        dto.setPublishedAt(dcc.getPublishedAt());
        dto.setCalibrationDate(dcc.getCalibrationDate());
        dto.setExpirationDate(dcc.getExpirationDate());
        dto.setHashXml(dcc.getHashXml());
        dto.setHashPdf(dcc.getHashPdf());
        dto.setCalibrationRequestId(dcc.getCalibrationRequestId());
        dto.setArchived(dcc.isArchived());
        dto.setStatus(calculateStatus(dcc));
        return dto;
    }

    private String calculateStatus(Dcc dcc) {
        if (dcc.isArchived()) return "ARCHIVED";
        if (dcc.getSensor() == null) return "GREY";
        if (!dcc.isPdfValid() || !dcc.isXmlValid()) return "RED";
        if (dcc.getPublishedAt() != null) return "BLUE";
        if (dcc.getExpirationDate() != null && dcc.getExpirationDate().isBefore(OffsetDateTime.now())) return "YELLOW";
        return "GREEN";
    }

    // -------------------------------------------------------------------------
    // SECURITY HELPERS
    // -------------------------------------------------------------------------
    private String getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Jwt) {
            return ((Jwt) principal).getSubject();
        }
        return principal.toString();
    }

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_app-admin"));
    }

    private User getOrCreateCurrentUser() {
        String userId = getCurrentUserId();
        return userRepository.findById(userId).orElseGet(() -> {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = new User();
            user.setUserId(userId);
            if (principal instanceof Jwt) {
                Jwt jwt = (Jwt) principal;
                user.setName(jwt.getClaimAsString("given_name"));
                user.setSurname(jwt.getClaimAsString("family_name"));
                user.setEmail(jwt.getClaimAsString("email"));
            }
            return userRepository.save(user);
        });
    }
}
