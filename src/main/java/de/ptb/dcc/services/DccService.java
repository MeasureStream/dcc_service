package de.ptb.dcc.services;

import de.ptb.dcc.dtos.DccUpdateRequest;
import de.ptb.dcc.dtos.DccValidationResultDto;
import de.ptb.dcc.entities.Dcc;
import de.ptb.dcc.entities.MeasurementUnit;
import de.ptb.dcc.entities.User;
import de.ptb.dcc.repositories.DccRepository;
import de.ptb.dcc.repositories.MeasurementUnitRepository;
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
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DccService {

    private final DccRepository dccRepository;
    private final MeasurementUnitRepository muRepository;
    private final UserRepository userRepository;
    private final DccSigningService signingService;
    private final S3Service s3Service;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemimeg.backend.url}")
    private String gemimegBackendUrl;

    public DccService(DccRepository dccRepository, MeasurementUnitRepository muRepository,
            UserRepository userRepository, DccSigningService signingService, S3Service s3Service) {
        this.dccRepository = dccRepository;
        this.muRepository = muRepository;
        this.userRepository = userRepository;
        this.signingService = signingService;
        this.s3Service = s3Service;
    }

    public Page<Dcc> listDccs(String muId, Boolean template, OffsetDateTime createdFrom, OffsetDateTime createdTo,
            String orderBy, String orderDir, int limit, int offset) {

        Sort sort = Sort.by(Sort.Direction.fromString(orderDir), orderBy);
        Pageable pageable = PageRequest.of(offset / limit, limit, sort);

        String currentUserId = getCurrentUserId();
        boolean isAdmin = isAdmin();

        Specification<Dcc> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!isAdmin) {
                predicates.add(cb.equal(root.get("user").get("userId"), currentUserId));
            }

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

    public List<MeasurementUnit> listMus(boolean all) {
        if (isAdmin()) {
            return muRepository.findAll();
        } else {
            return muRepository.findAllByUser_UserId(getCurrentUserId());
        }
    }

    public List<MeasurementUnit> listPublicMus(boolean all) {
        List<MeasurementUnit> baseMus = (all || isAdmin()) ? muRepository.findAll()
                : muRepository.findAllByUser_UserId(getCurrentUserId());
        return baseMus.stream()
                .filter(mu -> dccRepository.existsByMuAndPublishedAtIsNotNull(mu))
                .collect(Collectors.toList());
    }

    public Optional<Dcc> getPublishedDccByMuId(Long muId) {
        return muRepository.findById(muId)
                .flatMap(mu -> dccRepository.findByMuAndPublishedAtIsNotNull(mu).stream().findFirst());
    }

    @Transactional
    public Dcc createDcc(String muId, String name, String dccJson) {
        Dcc dcc = new Dcc();
        dcc.setName(name);
        dcc.setDccJson(dccJson != null ? dccJson : "{}");
        dcc.setCreatedBy(getCurrentUserId());
        dcc.setUser(getOrCreateCurrentUser());

        if (muId != null && !muId.isEmpty()) {
            try {
                Long id = Long.parseLong(muId);
                muRepository.findById(id).ifPresent(mu -> {
                    if (!isAdmin() && !mu.getUser().getUserId().equals(getCurrentUserId())) {
                        throw new RuntimeException("You don't have access to this Measurement Unit");
                    }
                    dcc.setMu(mu);
                });
            } catch (NumberFormatException e) {
            }
        }

        return dccRepository.save(dcc);
    }

    public Optional<Dcc> getDcc(Long dccId) {
        if (isAdmin()) {
            return dccRepository.findById(dccId);
        }
        return dccRepository.findByIdAndUser_UserId(dccId, getCurrentUserId());
    }

    @Transactional
    public Optional<Dcc> updateDcc(Long dccId, DccUpdateRequest request) {
        Optional<Dcc> existingDcc = isAdmin() ? dccRepository.findById(dccId)
                : dccRepository.findByIdAndUser_UserId(dccId, getCurrentUserId());

        return existingDcc.map(dcc -> {
            if (request.getName() != null)
                dcc.setName(request.getName());
            if (request.getDccJson() != null)
                dcc.setDccJson(request.getDccJson());
            if (request.getCalibrationDate() != null)
                dcc.setCalibrationDate(request.getCalibrationDate());
            if (request.getExpirationDate() != null)
                dcc.setExpirationDate(request.getExpirationDate());
            if (request.getMuId() != null) {
                if (request.getMuId().isEmpty()) {
                    dcc.setMu(null);
                } else {
                    try {
                        Long muId = Long.parseLong(request.getMuId());
                        muRepository.findById(muId).ifPresent(mu -> {
                            if (!isAdmin() && !mu.getUser().getUserId().equals(getCurrentUserId())) {
                                throw new RuntimeException("You don't have access to this Measurement Unit");
                            }
                            dcc.setMu(mu);
                        });
                    } catch (NumberFormatException e) {
                    }
                }
            }
            return dccRepository.save(dcc);
        });
    }

    @Transactional
    public Dcc validateDcc(Long dccId, String fileType) {
        System.out.println("=== VALIDATE DCC STARTED ===");

        Dcc dcc = (isAdmin() ? dccRepository.findById(dccId)
                : dccRepository.findByIdAndUser_UserId(dccId, getCurrentUserId()))
                .orElseThrow(() -> new EntityNotFoundException("DCC not found"));

        System.out.println("DCC found in database:");
        System.out.println("  - Name: " + dcc.getName());
        System.out.println("  - Current XML Valid: " + dcc.isXmlValid());
        System.out.println("  - Current PDF Valid: " + dcc.isPdfValid());
        System.out.println("  - Current XML URL: " + dcc.getXmlUrl());
        System.out.println("  - Current PDF URL: " + dcc.getPdfUrl());

        try {
            System.out.println("=== Starting Validation Chain for DCC ID: " + dccId + " ===");

            // 1. Conversion
            System.out.println("Converting JSON to XML/PDF via " + gemimegBackendUrl + "...");
            String xmlContent = convertToXml(dcc.getDccJson());
            System.out.println(
                    "XML Content length: " + (xmlContent != null ? xmlContent.length() : "null") + " characters");

            byte[] pdfContentBytes = convertToPdf(dcc.getDccJson());
            System.out.println(
                    "PDF Content length: " + (pdfContentBytes != null ? pdfContentBytes.length : "null") + " bytes");

            // 2. Signing and Verification
            System.out.println("Starting signing and verification process...");
            DccSigningService.SigningResult signingResult = signingService.performSigningAndVerification(dcc,
                    xmlContent, pdfContentBytes);

            if (signingResult != null) {

                // 3. Upload to S3 (New function call)
                System.out.println("Starting S3 upload process...");
                uploadToS3(dcc, signingResult);

                System.out.println("After S3 upload:");
                System.out.println("  - XML URL: " + dcc.getXmlUrl());
                System.out.println("  - PDF URL: " + dcc.getPdfUrl());

                // 4. Update DCC status from result
                System.out.println("Updating DCC entity with validation results...");
                dcc.setXmlValid(signingResult.xmlValid);
                dcc.setPdfValid(signingResult.pdfValid);
                dcc.setHashXml(signingResult.hashXml);
                dcc.setHashPdf(signingResult.hashPdf);

                // 5. Cleanup signed files
                System.out.println("Cleaning up temporary signed files...");
                if (signingResult.signedXml != null) {
                    signingResult.signedXml.delete();
                    System.out.println("  - Signed XML file deleted");
                }
                if (signingResult.signedPdf != null) {
                    signingResult.signedPdf.delete();
                    System.out.println("  - Signed PDF file deleted");
                }
            } else {
                System.out.println("ERROR: Signing result is null!");
            }

            System.out.println("Calculating final status...");

        } catch (Exception e) {
            System.err.println("[ERROR] Validation chain failed: " + e.getMessage());
            System.err.println("Stack trace:");
            e.printStackTrace();
        }

        System.out.println("Saving DCC to database...");
        Dcc savedDcc = dccRepository.save(dcc);
        System.out.println("=== VALIDATE DCC COMPLETED ===");

        return savedDcc;
    }

    private void uploadToS3(Dcc dcc, DccSigningService.SigningResult result) {
        if (result.xmlValid && result.signedXml != null) {
            System.out.println("Uploading signed XML to S3...");
            String xmlKey = "dcc-" + dcc.getId() + ".xml";
            String s3Url = s3Service.uploadFile(xmlKey, result.signedXml, "application/xml");
            if (s3Url != null) {
                dcc.setXmlUrl("/api/dcc/s3/" + dcc.getId() + "/xml");
            } else {
                System.err.println("[ERROR] Failed to upload XML of DCC ID: " + dcc.getId());
            }
        }

        if (result.pdfValid && result.signedPdf != null) {
            System.out.println("Uploading signed PDF to S3...");
            String pdfKey = "dcc-" + dcc.getId() + ".pdf";
            String s3Url = s3Service.uploadFile(pdfKey, result.signedPdf, "application/pdf");
            if (s3Url != null) {
                dcc.setPdfUrl("/api/dcc/s3/" + dcc.getId() + "/pdf");
            } else {
                System.err.println("[ERROR] Failed to upload PDF of DCC ID: " + dcc.getId());
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

    @Transactional
    public Dcc updateDccJson(Long dccId, String dccJson) {
        Dcc dcc = (isAdmin() ? dccRepository.findById(dccId)
                : dccRepository.findByIdAndUser_UserId(dccId, getCurrentUserId()))
                .orElseThrow(() -> new EntityNotFoundException("DCC not found"));
        dcc.setDccJson(dccJson);
        return dccRepository.save(dcc);
    }

    @Transactional
    public Dcc publishDcc(Long dccId) {
        Dcc dcc = (isAdmin() ? dccRepository.findById(dccId)
                : dccRepository.findByIdAndUser_UserId(dccId, getCurrentUserId()))
                .orElseThrow(() -> new EntityNotFoundException("DCC not found"));

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
    }

    @Transactional
    public Dcc unpublishDcc(Long dccId) {
        Dcc dcc = (isAdmin() ? dccRepository.findById(dccId)
                : dccRepository.findByIdAndUser_UserId(dccId, getCurrentUserId()))
                .orElseThrow(() -> new EntityNotFoundException("DCC not found"));
        dcc.setPublishedAt(null);
        return dccRepository.save(dcc);
    }

    @Transactional
    public void deleteDcc(Long dccId) {
        Dcc dcc = (isAdmin() ? dccRepository.findById(dccId)
                : dccRepository.findByIdAndUser_UserId(dccId, getCurrentUserId()))
                .orElseThrow(() -> new EntityNotFoundException("DCC not found"));
        dccRepository.delete(dcc);
    }

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

    private de.ptb.dcc.dtos.DccDto mapToDto(Dcc dcc) {
        de.ptb.dcc.dtos.DccDto dto = new de.ptb.dcc.dtos.DccDto();
        dto.setId(dcc.getId());
        if (dcc.getMu() != null) {
            dto.setMuId(dcc.getMu().getId().toString());
        }
        dto.setName(dcc.getName());
        dto.setCreatedBy(dcc.getCreatedBy());
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
        dto.setStatus(calculateStatus(dcc));
        return dto;
    }

    private String calculateStatus(Dcc dcc) {
        if (!dcc.isPdfValid() || !dcc.isXmlValid()) {
            return "RED";
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (dcc.getExpirationDate() == null || dcc.getExpirationDate().isBefore(now)) {
            return "YELLOW";
        }
        return "GREEN";
    }

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
            if (principal instanceof Jwt) {
                Jwt jwt = (Jwt) principal;
                User user = new User();
                user.setUserId(userId);
                user.setName(jwt.getClaimAsString("given_name"));
                user.setSurname(jwt.getClaimAsString("family_name"));
                user.setEmail(jwt.getClaimAsString("email"));
                return userRepository.save(user);
            }
            User user = new User();
            user.setUserId(userId);
            return userRepository.save(user);
        });
    }
}
