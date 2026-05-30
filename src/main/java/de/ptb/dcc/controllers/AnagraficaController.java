package de.ptb.dcc.controllers;

import de.ptb.dcc.dtos.AnagraficaCreateRequest;
import de.ptb.dcc.dtos.AnagraficaDto;
import de.ptb.dcc.entities.CalibrationMethod;
import de.ptb.dcc.entities.ClientCompany;
import de.ptb.dcc.entities.MeasurestreamCompany;
import de.ptb.dcc.repositories.CalibrationMethodRepository;
import de.ptb.dcc.repositories.ClientCompanyRepository;
import de.ptb.dcc.repositories.MeasurestreamCompanyRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CRUD + clone per le 3 tabelle anagrafica:
 *   /api/anagrafica/methods
 *   /api/anagrafica/ms-companies
 *   /api/anagrafica/client-companies
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/anagrafica")
public class AnagraficaController {

    private final CalibrationMethodRepository methodRepo;
    private final MeasurestreamCompanyRepository msRepo;
    private final ClientCompanyRepository clientRepo;

    public AnagraficaController(CalibrationMethodRepository methodRepo,
                                 MeasurestreamCompanyRepository msRepo,
                                 ClientCompanyRepository clientRepo) {
        this.methodRepo = methodRepo;
        this.msRepo = msRepo;
        this.clientRepo = clientRepo;
    }

    // ══════════════════════════════════════════════════════════
    //  CALIBRATION METHODS
    // ══════════════════════════════════════════════════════════

    @GetMapping("/methods")
    public List<AnagraficaDto> listMethods() {
        return methodRepo.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @GetMapping("/methods/{id}")
    public ResponseEntity<AnagraficaDto> getMethod(@PathVariable Long id) {
        return methodRepo.findById(id)
                .map(m -> ResponseEntity.ok(toDto(m)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/methods")
    public AnagraficaDto createMethod(@RequestBody AnagraficaCreateRequest req) {
        CalibrationMethod m = new CalibrationMethod();
        m.setName(req.getName());
        m.setJsonData(req.getJsonData());
        return toDto(methodRepo.save(m));
    }

    @PutMapping("/methods/{id}")
    public ResponseEntity<AnagraficaDto> updateMethod(@PathVariable Long id,
                                                       @RequestBody AnagraficaCreateRequest req) {
        return methodRepo.findById(id).map(m -> {
            if (req.getName() != null) m.setName(req.getName());
            if (req.getJsonData() != null) m.setJsonData(req.getJsonData());
            return ResponseEntity.ok(toDto(methodRepo.save(m)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/methods/{id}")
    public ResponseEntity<Void> deleteMethod(@PathVariable Long id) {
        if (!methodRepo.existsById(id)) return ResponseEntity.notFound().build();
        methodRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/methods/{id}/clone")
    public ResponseEntity<AnagraficaDto> cloneMethod(@PathVariable Long id) {
        return methodRepo.findById(id).map(m -> {
            CalibrationMethod copy = new CalibrationMethod();
            copy.setName(m.getName() + "_copy");
            copy.setJsonData(m.getJsonData());
            return ResponseEntity.ok(toDto(methodRepo.save(copy)));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ══════════════════════════════════════════════════════════
    //  MEASURESTREAM COMPANIES
    // ══════════════════════════════════════════════════════════

    @GetMapping("/ms-companies")
    public List<AnagraficaDto> listMsCompanies() {
        return msRepo.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @GetMapping("/ms-companies/{id}")
    public ResponseEntity<AnagraficaDto> getMsCompany(@PathVariable Long id) {
        return msRepo.findById(id)
                .map(m -> ResponseEntity.ok(toDto(m)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/ms-companies")
    public AnagraficaDto createMsCompany(@RequestBody AnagraficaCreateRequest req) {
        MeasurestreamCompany ms = new MeasurestreamCompany();
        ms.setName(req.getName());
        ms.setJsonData(req.getJsonData());
        return toDto(msRepo.save(ms));
    }

    @PutMapping("/ms-companies/{id}")
    public ResponseEntity<AnagraficaDto> updateMsCompany(@PathVariable Long id,
                                                          @RequestBody AnagraficaCreateRequest req) {
        return msRepo.findById(id).map(ms -> {
            if (req.getName() != null) ms.setName(req.getName());
            if (req.getJsonData() != null) ms.setJsonData(req.getJsonData());
            return ResponseEntity.ok(toDto(msRepo.save(ms)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/ms-companies/{id}")
    public ResponseEntity<Void> deleteMsCompany(@PathVariable Long id) {
        if (!msRepo.existsById(id)) return ResponseEntity.notFound().build();
        msRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/ms-companies/{id}/clone")
    public ResponseEntity<AnagraficaDto> cloneMsCompany(@PathVariable Long id) {
        return msRepo.findById(id).map(ms -> {
            MeasurestreamCompany copy = new MeasurestreamCompany();
            copy.setName(ms.getName() + "_copy");
            copy.setJsonData(ms.getJsonData());
            return ResponseEntity.ok(toDto(msRepo.save(copy)));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ══════════════════════════════════════════════════════════
    //  CLIENT COMPANIES
    // ══════════════════════════════════════════════════════════

    @GetMapping("/client-companies")
    public List<AnagraficaDto> listClientCompanies() {
        return clientRepo.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @GetMapping("/client-companies/{id}")
    public ResponseEntity<AnagraficaDto> getClientCompany(@PathVariable Long id) {
        return clientRepo.findById(id)
                .map(c -> ResponseEntity.ok(toDto(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/client-companies")
    public AnagraficaDto createClientCompany(@RequestBody AnagraficaCreateRequest req) {
        ClientCompany cc = new ClientCompany();
        cc.setName(req.getName());
        cc.setJsonData(req.getJsonData());
        return toDto(clientRepo.save(cc));
    }

    @PutMapping("/client-companies/{id}")
    public ResponseEntity<AnagraficaDto> updateClientCompany(@PathVariable Long id,
                                                              @RequestBody AnagraficaCreateRequest req) {
        return clientRepo.findById(id).map(cc -> {
            if (req.getName() != null) cc.setName(req.getName());
            if (req.getJsonData() != null) cc.setJsonData(req.getJsonData());
            return ResponseEntity.ok(toDto(clientRepo.save(cc)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/client-companies/{id}")
    public ResponseEntity<Void> deleteClientCompany(@PathVariable Long id) {
        if (!clientRepo.existsById(id)) return ResponseEntity.notFound().build();
        clientRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/client-companies/{id}/clone")
    public ResponseEntity<AnagraficaDto> cloneClientCompany(@PathVariable Long id) {
        return clientRepo.findById(id).map(cc -> {
            ClientCompany copy = new ClientCompany();
            copy.setName(cc.getName() + "_copy");
            copy.setJsonData(cc.getJsonData());
            return ResponseEntity.ok(toDto(clientRepo.save(copy)));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Mapping helpers ───────────────────────────────────────

    private AnagraficaDto toDto(CalibrationMethod m) {
        AnagraficaDto d = new AnagraficaDto();
        d.setId(m.getId()); d.setName(m.getName());
        d.setJsonData(m.getJsonData());
        d.setCreatedAt(m.getCreatedAt()); d.setUpdatedAt(m.getUpdatedAt());
        return d;
    }

    private AnagraficaDto toDto(MeasurestreamCompany m) {
        AnagraficaDto d = new AnagraficaDto();
        d.setId(m.getId()); d.setName(m.getName());
        d.setJsonData(m.getJsonData());
        d.setCreatedAt(m.getCreatedAt()); d.setUpdatedAt(m.getUpdatedAt());
        return d;
    }

    private AnagraficaDto toDto(ClientCompany c) {
        AnagraficaDto d = new AnagraficaDto();
        d.setId(c.getId()); d.setName(c.getName());
        d.setJsonData(c.getJsonData());
        d.setCreatedAt(c.getCreatedAt()); d.setUpdatedAt(c.getUpdatedAt());
        return d;
    }
}
