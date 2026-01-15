package de.ptb.dcc.service.controller;

import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@CrossOrigin(origins = "*") // Allows access from the frontend
public class DccController {

    // Simple in-memory storage to mock a database
    private final Map<String, Object> dccStorage = new ConcurrentHashMap<>();

    /**
     * Endpoint to check if the service is up and running.
     * Matches GET /status
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("connected", true);
        return response;
    }

    /**
     * Endpoint to retrieve a DCC by its ID.
     * Matches GET /dcc/{id}
     */
    @GetMapping("/dcc/{id}")
    public Object getDccById(@PathVariable String id) {
        if (dccStorage.containsKey(id)) {
            return dccStorage.get(id);
        }

        // Return a default valid-looking DCC DTO structure if not found
        // This follows the structure expected by the frontend
        Map<String, Object> mockDcc = new HashMap<>();
        Map<String, Object> adminData = new HashMap<>();
        adminData.put("uniqueIdentifier", id);
        adminData.put("countryCode", "DE");
        adminData.put("performanceLocation", "laboratory");

        // Mocking customer and lab data structures
        Map<String, Object> customer = new HashMap<>();
        customer.put("name", createMockName("Remote JAva Customer " + id));
        adminData.put("customer", customer);

        Map<String, Object> lab = new HashMap<>();
        Map<String, Object> labContact = new HashMap<>();
        labContact.put("name", createMockName("Remote Lab"));
        lab.put("contact", labContact);
        adminData.put("calibrationLaboratory", lab);

        adminData.put("items", new Object[]{});
        adminData.put("responsiblePersons", new Object[]{});

        mockDcc.put("administrativeData", adminData);
        mockDcc.put("measurementResults", new Object[]{});

        return mockDcc;
    }

    /**
     * Endpoint to save a DCC by its ID.
     * Matches POST /dcc/{id}
     */
    @PostMapping("/dcc/{id}")
    public Map<String, Object> saveDcc(@PathVariable String id, @RequestBody Object dccData) {
        dccStorage.put(id, dccData);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return response;
    }

    /**
     * Helper to create the nested name structure required by the DCC DTO
     */
    private Map<String, Object> createMockName(String text) {
        Map<String, Object> name = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        content.put("lang", "en");
        content.put("text", text);
        name.put("content", new Object[]{content});
        return name;
    }
}
