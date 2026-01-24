package de.ptb.dcc.controllers;

import de.ptb.dcc.dtos.DccCreateRequest;
import de.ptb.dcc.dtos.DccDto;
import de.ptb.dcc.dtos.DccUpdateRequest;
import de.ptb.dcc.dtos.MeasurementUnitDto;
import de.ptb.dcc.entities.Dcc;
import de.ptb.dcc.entities.MeasurementUnit;
import de.ptb.dcc.services.DccService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
public class DccController {

    private final DccService dccService;

    public DccController(DccService dccService) {
        this.dccService = dccService;
    }

    @GetMapping("/api/dcc")
    public ResponseEntity<List<DccDto>> listDccs(
            @RequestParam(required = false) String muId,
            @RequestParam(required = false, defaultValue = "false") boolean template,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdTo,
            @RequestParam(defaultValue = "createdAt") String orderBy,
            @RequestParam(defaultValue = "desc") String orderDir,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        Page<Dcc> page = dccService.listDccs(muId, template, createdFrom, createdTo, orderBy, orderDir, limit, offset);
        List<DccDto> dtos = page.getContent().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/api/mus")
    public ResponseEntity<List<MeasurementUnitDto>> listMus(
            @RequestParam(required = false, defaultValue = "false") boolean all) {
        // For now mock userId or get from security context if implemented
        String userId = "test-user";
        List<MeasurementUnit> mus = dccService.listMus(userId, all);
        List<MeasurementUnitDto> dtos = mus.stream()
                .map(this::mapToMuDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/api/public/mus")
    public ResponseEntity<List<MeasurementUnitDto>> listPublicMus(
            @RequestParam(required = false, defaultValue = "false") boolean all) {
        String userId = "test-user"; // Mock userId
        List<MeasurementUnit> mus = dccService.listPublicMus(userId, all);
        List<MeasurementUnitDto> dtos = mus.stream()
                .map(this::mapToMuDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/api/public/dcc/{muId}")
    public ResponseEntity<DccDto> getPublicDcc(@PathVariable Long muId) {
        return dccService.getPublishedDccByMuId(muId)
                .map(dcc -> ResponseEntity.ok(mapToDto(dcc)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/dcc")
    public ResponseEntity<DccDto> createDcc(@RequestBody DccCreateRequest request) {
        String createdBy = "anonymous";
        Dcc dcc = dccService.createDcc(request.getMuId(), request.getName(), createdBy, request.getDccJson());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToDto(dcc));
    }

    @GetMapping("/api/dcc/{dccId}")
    public ResponseEntity<DccDto> getDcc(@PathVariable Long dccId) {
        return dccService.getDcc(dccId)
                .map(dcc -> ResponseEntity.ok(mapToDto(dcc)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/api/dcc/{dccId}")
    public ResponseEntity<DccDto> updateDcc(@PathVariable Long dccId, @RequestBody DccUpdateRequest request) {
        return dccService.updateDcc(dccId, request.getName(), request.getDccJson())
                .map(dcc -> ResponseEntity.ok(mapToDto(dcc)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/dcc/{dccId}/validate")
    public ResponseEntity<DccDto> validateDcc(
            @PathVariable Long dccId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileType") String fileType) {

        Dcc dcc = dccService.validateDcc(dccId, fileType);
        return ResponseEntity.ok(mapToDto(dcc));
    }

    @PostMapping("/api/dcc/{dccId}/json")
    public ResponseEntity<DccDto> updateDccJson(
            @PathVariable Long dccId,
            @RequestBody String dccJson) {
        Dcc dcc = dccService.updateDccJson(dccId, dccJson);
        return ResponseEntity.ok(mapToDto(dcc));
    }

    @PostMapping("/api/dcc/{dccId}/publish")
    public ResponseEntity<DccDto> publishDcc(@PathVariable Long dccId) {
        Dcc dcc = dccService.publishDcc(dccId);
        return ResponseEntity.ok(mapToDto(dcc));
    }

    @DeleteMapping("/api/dcc/{dccId}")
    public ResponseEntity<Void> deleteDcc(@PathVariable Long dccId) {
        dccService.deleteDcc(dccId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/dcc/{dccId}/download")
    public ResponseEntity<byte[]> downloadDcc(@PathVariable Long dccId,
            @RequestParam(defaultValue = "PDF") String fileType) {
        byte[] content = "Mock DCC Content".getBytes();
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=\"dcc-" + dccId + "." + fileType.toLowerCase() + "\"")
                .body(content);
    }

    @GetMapping("/me")
    public ResponseEntity<String> getMe() {
        return ResponseEntity.ok(createMeResponse());
    }

    private String createMeResponse() {
        return """
                {
                    "name": "test",
                    "loginUrl": "https://dev.christiandellisanti.uk/oauth2/authorization/gateway",
                    "logoutUrl": "https://dev.christiandellisanti.uk/logout",
                    "principal": {
                        "authorities": [
                            {
                                "authority": "OIDC_USER",
                                "attributes": {
                                    "at_hash": "aW7JqqttWsJcvQLF201JlQ",
                                    "sub": "c09c30f4-7625-4e49-af6b-f99bc577b1ef",
                                    "email_verified": true,
                                    "iss": "https://auth.christiandellisanti.uk/realms/measurestream-dev",
                                    "typ": "ID",
                                    "preferred_username": "test",
                                    "given_name": "test",
                                    "nonce": "QXVHu2QJQR911xFBLm4HjOsSK4mjtdxjCzdhg0Pmfy4",
                                    "sid": "f312af93-9187-decb-3473-9cac31023182",
                                    "aud": [
                                        "gateway-dev"
                                    ],
                                    "acr": "1",
                                    "realm_access": {
                                        "roles": [
                                            "offline_access",
                                            "default-roles-measurestream-dev",
                                            "uma_authorization"
                                        ]
                                    },
                                    "azp": "gateway-dev",
                                    "auth_time": "2026-01-22T18:22:37Z",
                                    "name": "test test",
                                    "exp": "2026-01-22T18:27:38Z",
                                    "family_name": "test",
                                    "iat": "2026-01-22T18:22:38Z",
                                    "email": "test@test.it",
                                    "jti": "0e13aead-5035-8e3f-7e6a-63dc00705e5a"
                                },
                                "idToken": {
                                    "tokenValue": "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJZU292MGMyYXRjYnljZEtSV0dWTFdrWWkxM2tGaEVweFF1VDIyMU1CLUJRIn0.eyJleHAiOjE3NjkxMDY0NTgsImlhdCI6MTc2OTEwNjE1OCwiYXV0aF90aW1lIjoxNzY5MTA2MTU3LCJqdGkiOiIwZTEzYWVhZC01MDM1LThlM2YtN2U2YS02M2RjMDA3MDVlNWEiLCJpc3MiOiJodHRwczovL2F1dGguY2hyaXN0aWFuZGVsbGlzYW50aS51ay9yZWFsbXMvbWVhc3VyZXN0cmVhbS1kZXYiLCJhdWQiOiJnYXRld2F5LWRldiIsInN1YiI6ImMwOWMzMGY0LTc2MjUtNGU0OS1hZjZiLWY5OWJjNTc3YjFlZiIsInR5cCI6IklEIiwiYXpwIjoiZ2F0ZXdheS1kZXYiLCJub25jZSI6IlFYVkh1MlFKUVI5MTF4RkJMbTRIak9zU0s0bWp0ZHhqQ3pkaGcwUG1meTQiLCJzaWQiOiJmMzEyYWY5My05MTg3LWRlY2ItMzQ3My05Y2FjMzEwMjMxODIiLCJhdF9oYXNoIjoiYVc3SnFxdHRXc0pjdlFMRjIwMUpsUSIsImFjciI6IjEiLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbIm9mZmxpbmVfYWNjZXNzIiwiZGVmYXVsdC1yb2xlcy1tZWFzdXJlc3RyZWFtLWRldiIsInVtYV9hdXRob3JpemF0aW9uIl19LCJuYW1lIjoidGVzdCB0ZXN0IiwicHJlZmVycmVkX3VzZXJuYW1lIjoidGVzdCIsImdpdmVuX25hbWUiOiJ0ZXN0IiwiZmFtaWx5X25hbWUiOiJ0ZXN0IiwiZW1haWwiOiJ0ZXN0QHRlc3QuaXQifQ.yvO77OdcAmZtVFcUxe4y4uvxsLSWszkEgYF9dXDkRlUq0-2_68SPlDCIq2uObGxkVnn2l2Zon6ufTMCDX2-qromor0Lw0rSYWo6ZmCyjThOA9jHBhKf098aJ0RaTpccu-khn9DZyCUBmgCsqpAIK-gZ5LF-38oqy-jRdzcmGa8OZEkvxWYRJXb6_NulG94_y99Oq6Lg55edH9umjxYEp9Eia38uxJIviKi0TxLckHhW7qnq8lGTbrnswU1dWvtp6IidVNNli-Hkp-jAOcKOy7jkN3vQBw5z_E2UP3pmQgBGtqT0dky6mGMLsZBSiUidLqCpP3Da0xsfLTkk2-mC_Sg",
                                    "issuedAt": "2026-01-22T18:22:38Z",
                                    "expiresAt": "2026-01-22T18:27:38Z",
                                    "claims": {
                                        "at_hash": "aW7JqqttWsJcvQLF201JlQ",
                                        "sub": "c09c30f4-7625-4e49-af6b-f99bc577b1ef",
                                        "email_verified": true,
                                        "iss": "https://auth.christiandellisanti.uk/realms/measurestream-dev",
                                        "typ": "ID",
                                        "preferred_username": "test",
                                        "given_name": "test",
                                        "nonce": "QXVHu2QJQR911xFBLm4HjOsSK4mjtdxjCzdhg0Pmfy4",
                                        "sid": "f312af93-9187-decb-3473-9cac31023182",
                                        "aud": [
                                            "gateway-dev"
                                        ],
                                        "acr": "1",
                                        "realm_access": {
                                            "roles": [
                                                "offline_access",
                                                "default-roles-measurestream-dev",
                                                "uma_authorization"
                                            ]
                                        },
                                        "azp": "gateway-dev",
                                        "auth_time": "2026-01-22T18:22:37Z",
                                        "name": "test test",
                                        "exp": "2026-01-22T18:27:38Z",
                                        "iat": "2026-01-22T18:22:38Z",
                                        "family_name": "test",
                                        "jti": "0e13aead-5035-8e3f-7e6a-63dc00705e5a",
                                        "email": "test@test.it"
                                    },
                                    "nonce": "QXVHu2QJQR911xFBLm4HjOsSK4mjtdxjCzdhg0Pmfy4",
                                    "subject": "c09c30f4-7625-4e49-af6b-f99bc577b1ef",
                                    "issuer": "https://auth.christiandellisanti.uk/realms/measurestream-dev",
                                    "audience": [
                                        "gateway-dev"
                                    ],
                                    "authenticatedAt": "2026-01-22T18:22:37Z",
                                    "authenticationContextClass": "1",
                                    "authenticationMethods": null,
                                    "authorizedParty": "gateway-dev",
                                    "accessTokenHash": "aW7JqqttWsJcvQLF201JlQ",
                                    "authorizationCodeHash": null,
                                    "givenName": "test",
                                    "address": {
                                        "formatted": null,
                                        "streetAddress": null,
                                        "locality": null,
                                        "region": null,
                                        "postalCode": null,
                                        "country": null
                                    },
                                    "locale": null,
                                    "zoneInfo": null,
                                    "fullName": "test test",
                                    "profile": null,
                                    "preferredUsername": "test",
                                    "familyName": "test",
                                    "middleName": null,
                                    "nickName": null,
                                    "picture": null,
                                    "website": null,
                                    "email": "test@test.it",
                                    "emailVerified": true,
                                    "gender": null,
                                    "birthdate": null,
                                    "phoneNumber": null,
                                    "phoneNumberVerified": null,
                                    "updatedAt": null
                                },
                                "userInfo": {
                                    "claims": {
                                        "sub": "c09c30f4-7625-4e49-af6b-f99bc577b1ef",
                                        "email_verified": true,
                                        "name": "test test",
                                        "preferred_username": "test",
                                        "given_name": "test",
                                        "family_name": "test",
                                        "email": "test@test.it"
                                    },
                                    "givenName": "test",
                                    "address": {
                                        "formatted": null,
                                        "streetAddress": null,
                                        "locality": null,
                                        "region": null,
                                        "postalCode": null,
                                        "country": null
                                    },
                                    "locale": null,
                                    "zoneInfo": null,
                                    "fullName": "test test",
                                    "profile": null,
                                    "subject": "c09c30f4-7625-4e49-af6b-f99bc577b1ef",
                                    "preferredUsername": "test",
                                    "familyName": "test",
                                    "middleName": null,
                                    "nickName": null,
                                    "picture": null,
                                    "website": null,
                                    "email": "test@test.it",
                                    "emailVerified": true,
                                    "gender": null,
                                    "birthdate": null,
                                    "phoneNumber": null,
                                    "phoneNumberVerified": null,
                                    "updatedAt": null
                                }
                            },
                            {
                                "authority": "SCOPE_email"
                            },
                            {
                                "authority": "SCOPE_offline_access"
                            },
                            {
                                "authority": "SCOPE_openid"
                            },
                            {
                                "authority": "SCOPE_profile"
                            }
                        ],
                        "attributes": {
                            "at_hash": "aW7JqqttWsJcvQLF201JlQ",
                            "sub": "c09c30f4-7625-4e49-af6b-f99bc577b1ef",
                            "email_verified": true,
                            "iss": "https://auth.christiandellisanti.uk/realms/measurestream-dev",
                            "typ": "ID",
                            "preferred_username": "test",
                            "given_name": "test",
                            "nonce": "QXVHu2QJQR911xFBLm4HjOsSK4mjtdxjCzdhg0Pmfy4",
                            "sid": "f312af93-9187-decb-3473-9cac31023182",
                            "aud": [
                                "gateway-dev"
                            ],
                            "acr": "1",
                            "realm_access": {
                                "roles": [
                                    "offline_access",
                                    "default-roles-measurestream-dev",
                                    "uma_authorization"
                                ]
                            },
                            "azp": "gateway-dev",
                            "auth_time": "2026-01-22T18:22:37Z",
                            "name": "test test",
                            "exp": "2026-01-22T18:27:38Z",
                            "family_name": "test",
                            "iat": "2026-01-22T18:22:38Z",
                            "email": "test@test.it",
                            "jti": "0e13aead-5035-8e3f-7e6a-63dc00705e5a"
                        },
                        "idToken": {
                            "tokenValue": "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJZU292MGMyYXRjYnljZEtSV0dWTFdrWWkxM2tGaEVweFF1VDIyMU1CLUJRIn0.eyJleHAiOjE3NjkxMDY0NTgsImlhdCI6MTc2OTEwNjE1OCwiYXV0aF90aW1lIjoxNzY5MTA2MTU3LCJqdGkiOiIwZTEzYWVhZC01MDM1LThlM2YtN2U2YS02M2RjMDA3MDVlNWEiLCJpc3MiOiJodHRwczovL2F1dGguY2hyaXN0aWFuZGVsbGlzYW50aS51ay9yZWFsbXMvbWVhc3VyZXN0cmVhbS1kZXYiLCJhdWQiOiJnYXRld2F5LWRldiIsInN1YiI6ImMwOWMzMGY0LTc2MjUtNGU0OS1hZjZiLWY5OWJjNTc3YjFlZiIsInR5cCI6IklEIiwiYXpwIjoiZ2F0ZXdheS1kZXYiLCJub25jZSI6IlFYVkh1MlFKUVI5MTF4RkJMbTRIak9zU0s0bWp0ZHhqQ3pkaGcwUG1meTQiLCJzaWQiOiJmMzEyYWY5My05MTg3LWRlY2ItMzQ3My05Y2FjMzEwMjMxODIiLCJhdF9oYXNoIjoiYVc3SnFxdHRXc0pjdlFMRjIwMUpsUSIsImFjciI6IjEiLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbIm9mZmxpbmVfYWNjZXNzIiwiZGVmYXVsdC1yb2xlcy1tZWFzdXJlc3RyZWFtLWRldiIsInVtYV9hdXRob3JpemF0aW9uIl19LCJuYW1lIjoidGVzdCB0ZXN0IiwicHJlZmVycmVkX3VzZXJuYW1lIjoidGVzdCIsImdpdmVuX25hbWUiOiJ0ZXN0IiwiZmFtaWx5X25hbWUiOiJ0ZXN0IiwiZW1haWwiOiJ0ZXN0QHRlc3QuaXQifQ.yvO77OdcAmZtVFcUxe4y4uvxsLSWszkEgYF9dXDkRlUq0-2_68SPlDCIq2uObGxkVnn2l2Zon6ufTMCDX2-qromor0Lw0rSYWo6ZmCyjThOA9jHBhKf098aJ0RaTpccu-khn9DZyCUBmgCsqpAIK-gZ5LF-38oqy-jRdzcmGa8OZEkvxWYRJXb6_NulG94_y99Oq6Lg55edH9umjxYEp9Eia38uxJIviKi0TxLckHhW7qnq8lGTbrnswU1dWvtp6IidVNNli-Hkp-jAOcKOy7jkN3vQBw5z_E2UP3pmQgBGtqT0dky6mGMLsZBSiUidLqCpP3Da0xsfLTkk2-mC_Sg",
                            "issuedAt": "2026-01-22T18:22:38Z",
                            "expiresAt": "2026-01-22T18:27:38Z",
                            "claims": {
                                "at_hash": "aW7JqqttWsJcvQLF201JlQ",
                                "sub": "c09c30f4-7625-4e49-af6b-f99bc577b1ef",
                                "email_verified": true,
                                "iss": "https://auth.christiandellisanti.uk/realms/measurestream-dev",
                                "typ": "ID",
                                "preferred_username": "test",
                                "given_name": "test",
                                "nonce": "QXVHu2QJQR911xFBLm4HjOsSK4mjtdxjCzdhg0Pmfy4",
                                "sid": "f312af93-9187-decb-3473-9cac31023182",
                                "aud": [
                                    "gateway-dev"
                                ],
                                "acr": "1",
                                "realm_access": {
                                    "roles": [
                                        "offline_access",
                                        "default-roles-measurestream-dev",
                                        "uma_authorization"
                                    ]
                                },
                                "azp": "gateway-dev",
                                "auth_time": "2026-01-22T18:22:37Z",
                                "name": "test test",
                                "exp": "2026-01-22T18:27:38Z",
                                "iat": "2026-01-22T18:22:38Z",
                                "family_name": "test",
                                "jti": "0e13aead-5035-8e3f-7e6a-63dc00705e5a",
                                "email": "test@test.it"
                            },
                            "nonce": "QXVHu2QJQR911xFBLm4HjOsSK4mjtdxjCzdhg0Pmfy4",
                            "subject": "c09c30f4-7625-4e49-af6b-f99bc577b1ef",
                            "issuer": "https://auth.christiandellisanti.uk/realms/measurestream-dev",
                            "audience": [
                                "gateway-dev"
                            ],
                            "authenticatedAt": "2026-01-22T18:22:37Z",
                            "authenticationContextClass": "1",
                            "authenticationMethods": null,
                            "authorizedParty": "gateway-dev",
                            "accessTokenHash": "aW7JqqttWsJcvQLF201JlQ",
                            "authorizationCodeHash": null,
                            "givenName": "test",
                            "address": {
                                "formatted": null,
                                "streetAddress": null,
                                "locality": null,
                                "region": null,
                                "postalCode": null,
                                "country": null
                            },
                            "locale": null,
                            "zoneInfo": null,
                            "fullName": "test test",
                            "profile": null,
                            "preferredUsername": "test",
                            "familyName": "test",
                            "middleName": null,
                            "nickName": null,
                            "picture": null,
                            "website": null,
                            "email": "test@test.it",
                            "emailVerified": true,
                            "gender": null,
                            "birthdate": null,
                            "phoneNumber": null,
                            "phoneNumberVerified": null,
                            "updatedAt": null
                        },
                        "userInfo": {
                            "claims": {
                                "sub": "c09c30f4-7625-4e49-af6b-f99bc577b1ef",
                                "email_verified": true,
                                "name": "test test",
                                "preferred_username": "test",
                                "given_name": "test",
                                "family_name": "test",
                                "email": "test@test.it"
                            },
                            "givenName": "test",
                            "address": {
                                "formatted": null,
                                "streetAddress": null,
                                "locality": null,
                                "region": null,
                                "postalCode": null,
                                "country": null
                            },
                            "locale": null,
                            "zoneInfo": null,
                            "fullName": "test test",
                            "profile": null,
                            "subject": "c09c30f4-7625-4e49-af6b-f99bc577b1ef",
                            "preferredUsername": "test",
                            "familyName": "test",
                            "middleName": null,
                            "nickName": null,
                            "picture": null,
                            "website": null,
                            "email": "test@test.it",
                            "emailVerified": true,
                            "gender": null,
                            "birthdate": null,
                            "phoneNumber": null,
                            "phoneNumberVerified": null,
                            "updatedAt": null
                        },
                        "claims": {
                            "at_hash": "aW7JqqttWsJcvQLF201JlQ",
                            "sub": "c09c30f4-7625-4e49-af6b-f99bc577b1ef",
                            "email_verified": true,
                            "iss": "https://auth.christiandellisanti.uk/realms/measurestream-dev",
                            "typ": "ID",
                            "preferred_username": "test",
                            "given_name": "test",
                            "nonce": "QXVHu2QJQR911xFBLm4HjOsSK4mjtdxjCzdhg0Pmfy4",
                            "sid": "f312af93-9187-decb-3473-9cac31023182",
                            "aud": [
                                "gateway-dev"
                            ],
                            "acr": "1",
                            "realm_access": {
                                "roles": [
                                    "offline_access",
                                    "default-roles-measurestream-dev",
                                    "uma_authorization"
                                ]
                            },
                            "azp": "gateway-dev",
                            "auth_time": "2026-01-22T18:22:37Z",
                            "name": "test test",
                            "exp": "2026-01-22T18:27:38Z",
                            "family_name": "test",
                            "iat": "2026-01-22T18:22:38Z",
                            "email": "test@test.it",
                            "jti": "0e13aead-5035-8e3f-7e6a-63dc00705e5a"
                        },
                        "name": "c09c30f4-7625-4e49-af6b-f99bc577b1ef",
                        "nonce": "QXVHu2QJQR911xFBLm4HjOsSK4mjtdxjCzdhg0Pmfy4",
                        "subject": "c09c30f4-7625-4e49-af6b-f99bc577b1ef",
                        "issuer": "https://auth.christiandellisanti.uk/realms/measurestream-dev",
                        "expiresAt": "2026-01-22T18:27:38Z",
                        "issuedAt": "2026-01-22T18:22:38Z",
                        "audience": [
                            "gateway-dev"
                        ],
                        "authenticatedAt": "2026-01-22T18:22:37Z",
                        "authenticationContextClass": "1",
                        "authenticationMethods": null,
                        "authorizedParty": "gateway-dev",
                        "accessTokenHash": "aW7JqqttWsJcvQLF201JlQ",
                        "authorizationCodeHash": null,
                        "givenName": "test",
                        "address": {
                            "formatted": null,
                            "streetAddress": null,
                            "locality": null,
                            "region": null,
                            "postalCode": null,
                            "country": null
                        },
                        "locale": null,
                        "zoneInfo": null,
                        "fullName": "test test",
                        "profile": null,
                        "preferredUsername": "test",
                        "familyName": "test",
                        "middleName": null,
                        "nickName": null,
                        "picture": null,
                        "website": null,
                        "email": "test@test.it",
                        "emailVerified": true,
                        "gender": null,
                        "birthdate": null,
                        "phoneNumber": null,
                        "phoneNumberVerified": null,
                        "updatedAt": null
                    },
                    "xsrfToken": "34b3a294-0ed7-4006-804e-847b540e0751",
                    "autorities": [
                        {
                            "authority": "OIDC_USER",
                            "attributes": {
                                "at_hash": "aW7JqqttWsJcvQLF201JlQ",
                                "sub": "c09c30f4-7625-4e49-af6b-f99bc577b1ef",
                                "email_verified": true,
                                "iss": "https://auth.christiandellisanti.uk/realms/measurestream-dev",
                                "typ": "ID",
                                "preferred_username": "test",
                                "given_name": "test",
                                "nonce": "QXVHu2QJQR911xFBLm4HjOsSK4mjtdxjCzdhg0Pmfy4",
                                "sid": "f312af93-9187-decb-3473-9cac31023182",
                                "aud": [
                                    "gateway-dev"
                                ],
                                "acr": "1",
                                "realm_access": {
                                    "roles": [
                                        "offline_access",
                                        "default-roles-measurestream-dev",
                                        "uma_authorization"
                                    ]
                                },
                                "azp": "gateway-dev",
                                "auth_time": "2026-01-22T18:22:37Z",
                                "name": "test test",
                                "exp": "2026-01-22T18:27:38Z",
                                "family_name": "test",
                                "iat": "2026-01-22T18:22:38Z",
                                "email": "test@test.it",
                                "jti": "0e13aead-5035-8e3f-7e6a-63dc00705e5a"
                            },
                            "idToken": {
                                "tokenValue": "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJZU292MGMyYXRjYnljZEtSV0dWTFdrWWkxM2tGaEVweFF1VDIyMU1CLUJRIn0.eyJleHAiOjE3NjkxMDY0NTgsImlhdCI6MTc2OTEwNjE1OCwiYXV0aF90aW1lIjoxNzY5MTA2MTU3LCJqdGkiOiIwZTEzYWVhZC01MDM1LThlM2YtN2U2YS02M2RjMDA3MDVlNWEiLCJpc3MiOiJodHRwczovL2F1dGguY2hyaXN0aWFuZGVsbGlzYW50aS51ay9yZWFsbXMvbWVhc3VyZXN0cmVhbS1kZXYiLCJhdWQiOiJnYXRld2F5LWRldiIsInN1YiI6ImMwOWMzMGY0LTc2MjUtNGU0OS1hZjZiLWY5OWJjNTc3YjFlZiIsInR5cCI6IklEIiwiYXpwIjoiZ2F0ZXdheS1kZXYiLCJub25jZSI6IlFYVkh1MlFKUVI5MTF4RkJMbTRIak9zU0s0bWp0ZHhqQ3pkaGcwUG1meTQiLCJzaWQiOiJmMzEyYWY5My05MTg3LWRlY2ItMzQ3My05Y2FjMzEwMjMxODIiLCJhdF9oYXNoIjoiYVc3SnFxdHRXc0pjdlFMRjIwMUpsUSIsImFjciI6IjEiLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbIm9mZmxpbmVfYWNjZXNzIiwiZGVmYXVsdC1yb2xlcy1tZWFzdXJlc3RyZWFtLWRldiIsInVtYV9hdXRob3JpemF0aW9uIl19LCJuYW1lIjoidGVzdCB0ZXN0IiwicHJlZmVycmVkX3VzZXJuYW1lIjoidGVzdCIsImdpdmVuX25hbWUiOiJ0ZXN0IiwiZmFtaWx5X25hbWUiOiJ0ZXN0IiwiZW1haWwiOiJ0ZXN0QHRlc3QuaXQifQ.yvO77OdcAmZtVFcUxe4y4uvxsLSWszkEgYF9dXDkRlUq0-2_68SPlDCIq2uObGxkVnn2l2Zon6ufTMCDX2-qromor0Lw0rSYWo6ZmCyjThOA9jHBhKf098aJ0RaTpccu-khn9DZyCUBmgCsqpAIK-gZ5LF-38oqy-jRdzcmGa8OZEkvxWYRJXb6_NulG94_y99Oq6Lg55edH9umjxYEp9Eia38uxJIviKi0TxLckHhW7qnq8lGTbrnswU1dWvtp6IidVNNli-Hkp-jAOcKOy7jkN3vQBw5z_E2UP3pmQgBGtqT0dky6mGMLsZBSiUidLqCpP3Da0xsfLTkk2-mC_Sg",
                                "issuedAt": "2026-01-22T18:22:38Z",
                                "expiresAt": "2026-01-22T18:27:38Z",
                                "claims": {
                                    "at_hash": "aW7JqqttWsJcvQLF201JlQ",
                                    "sub": "c09c30f4-7625-4e49-af6b-f99bc577b1ef",
                                    "email_verified": true,
                                    "iss": "https://auth.christiandellisanti.uk/realms/measurestream-dev",
                                    "typ": "ID",
                                    "preferred_username": "test",
                                    "given_name": "test",
                                    "nonce": "QXVHu2QJQR911xFBLm4HjOsSK4mjtdxjCzdhg0Pmfy4",
                                    "sid": "f312af93-9187-decb-3473-9cac31023182",
                                    "aud": [
                                        "gateway-dev"
                                    ],
                                    "acr": "1",
                                    "realm_access": {
                                        "roles": [
                                            "offline_access",
                                            "default-roles-measurestream-dev",
                                            "uma_authorization"
                                        ]
                                    },
                                    "azp": "gateway-dev",
                                    "auth_time": "2026-01-22T18:22:37Z",
                                    "name": "test test",
                                    "exp": "2026-01-22T18:27:38Z",
                                    "iat": "2026-01-22T18:22:38Z",
                                    "family_name": "test",
                                    "jti": "0e13aead-5035-8e3f-7e6a-63dc00705e5a",
                                    "email": "test@test.it"
                                },
                                "nonce": "QXVHu2QJQR911xFBLm4HjOsSK4mjtdxjCzdhg0Pmfy4",
                                "subject": "c09c30f4-7625-4e49-af6b-f99bc577b1ef",
                                "issuer": "https://auth.christiandellisanti.uk/realms/measurestream-dev",
                                "audience": [
                                    "gateway-dev"
                                ],
                                "authenticatedAt": "2026-01-22T18:22:37Z",
                                "authenticationContextClass": "1",
                                "authenticationMethods": null,
                                "authorizedParty": "gateway-dev",
                                "accessTokenHash": "aW7JqqttWsJcvQLF201JlQ",
                                "authorizationCodeHash": null,
                                "givenName": "test",
                                "address": {
                                    "formatted": null,
                                    "streetAddress": null,
                                    "locality": null,
                                    "region": null,
                                    "postalCode": null,
                                    "country": null
                                },
                                "locale": null,
                                "zoneInfo": null,
                                "fullName": "test test",
                                "profile": null,
                                "preferredUsername": "test",
                                "familyName": "test",
                                "middleName": null,
                                "nickName": null,
                                "picture": null,
                                "website": null,
                                "email": "test@test.it",
                                "emailVerified": true,
                                "gender": null,
                                "birthdate": null,
                                "phoneNumber": null,
                                "phoneNumberVerified": null,
                                "updatedAt": null
                            },
                            "userInfo": {
                                "claims": {
                                    "sub": "c09c30f4-7625-4e49-af6b-f99bc577b1ef",
                                    "email_verified": true,
                                    "name": "test test",
                                    "preferred_username": "test",
                                    "given_name": "test",
                                    "family_name": "test",
                                    "email": "test@test.it"
                                },
                                "givenName": "test",
                                "address": {
                                    "formatted": null,
                                    "streetAddress": null,
                                    "locality": null,
                                    "region": null,
                                    "postalCode": null,
                                    "country": null
                                },
                                "locale": null,
                                "zoneInfo": null,
                                "fullName": "test test",
                                "profile": null,
                                "subject": "c09c30f4-7625-4e49-af6b-f99bc577b1ef",
                                "preferredUsername": "test",
                                "familyName": "test",
                                "middleName": null,
                                "nickName": null,
                                "picture": null,
                                "website": null,
                                "email": "test@test.it",
                                "emailVerified": true,
                                "gender": null,
                                "birthdate": null,
                                "phoneNumber": null,
                                "phoneNumberVerified": null,
                                "updatedAt": null
                            }
                        },
                        {
                            "authority": "SCOPE_email"
                        },
                        {
                            "authority": "SCOPE_offline_access"
                        },
                        {
                            "authority": "SCOPE_openid"
                        },
                        {
                            "authority": "SCOPE_profile"
                        }
                    ]
                }
                """;
    }

    private MeasurementUnitDto mapToMuDto(MeasurementUnit mu) {
        MeasurementUnitDto dto = new MeasurementUnitDto();
        dto.setId(mu.getId());
        dto.setType(mu.getType());
        dto.setMeasuresUnit(mu.getMeasuresUnit());
        dto.setNetworkId(mu.getNetworkId());
        if (mu.getNode() != null) {
            dto.setNodeId(mu.getNode().getId());
        }
        if (mu.getUser() != null) {
            dto.setOwnerId(mu.getUser().getUserId());
        }
        return dto;
    }

    private DccDto mapToDto(Dcc dcc) {
        DccDto dto = new DccDto();
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
        dto.setStatus(calculateStatus(dcc));
        return dto;
    }

    private String calculateStatus(Dcc dcc) {
        if (!dcc.isPdfValid() || !dcc.isXmlValid()) {
            return "RED";
        }
        if (dcc.getPublishedAt() == null) {
            return "YELLOW";
        }
        return "GREEN";
    }
}
