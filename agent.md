# dcc_service — Agent Guide

## Prima cosa da fare
Esplora la struttura della cartella `backend/dcc_service` e leggi le entity in `src/main/java/de/ptb/dcc/entities/` prima di fare qualsiasi modifica.

## Ruolo del servizio
`dcc_service` gestisce il ciclo di vita dei Digital Calibration Certificate (DCC): creazione, validazione (firma XML + PDF), pubblicazione pubblica, verifica esterna. Non sostituisce `sensor-manager`: i due servizi sono complementari e indipendenti.

## Struttura
```
src/main/java/de/ptb/dcc/
├── configurations/
│   └── SecurityConfig.java          # OAuth2 resource server, ruoli da JWT Keycloak
├── controllers/
│   └── DccController.java           # REST API
├── dtos/
│   ├── DccCreateRequest.java        # input: sensorId, name, dccJson
│   ├── DccUpdateRequest.java        # input: sensorId, name, dccJson, date
│   ├── DccDto.java                  # output DCC con sensorId, status
│   ├── SensorDto.java               # output sensore: id, modelName, muExtendedId, cuDevEui, ownerId
│   ├── DccValidationResultDto.java  # output validazione esterna
│   └── MeasurementUnitDto.java      # non più usato attivamente
├── entities/
│   ├── User.java                    # app_user — userId = Keycloak sub
│   ├── ControlUnit.java             # PASSIVA — specchio sensor-manager; user nullable (null = unclaimed)
│   ├── MeasurementUnit.java         # PASSIVA — extendedId=EUID, FK control_unit_id
│   ├── Sensor.java                  # PASSIVA — FK mu_id; getOwner() risale a CU.user
│   ├── Dcc.java                     # ATTIVA — FK sensor_id, user_user_id
│   ├── Calibration.java             # da Kafka topic "calibrations"
│   └── CalibratorRequest.java       # da Kafka topic "calibration.request"
├── repositories/
│   ├── DccRepository.java
│   ├── SensorRepository.java
│   ├── ControlUnitRepository.java
│   ├── MeasurementUnitRepository.java
│   ├── UserRepository.java
│   ├── CalibrationRepository.java
│   └── CalibratorRequestRepository.java
├── services/
│   ├── DccService.java                      # logica principale
│   ├── DccSigningService.java               # firma XML (XMLDSig) e PDF (PAdES)
│   ├── S3Service.java                       # upload/download Garage (S3-compat)
│   ├── KafkaCalibrationConsumer.java        # ascolta "calibrations", salva Calibration
│   └── KafkaCalibrationRequestConsumer.java # ascolta "calibration.request", risponde su "calibration.response"
└── utils/
    └── SigningUtils.java                    # carica chiavi PKI, firma, verifica hash
```

## Modello dati (catena di ownership)
```
User (app_user)
 └─ ControlUnit          user_user_id nullable (null = CU non reclamata)
      └─ MeasurementUnit  FK control_unit_id nullable
           └─ Sensor       FK mu_id nullable
                └─ Dcc     FK sensor_id nullable (null = DCC template)
```

## Regole admin / utente normale
| Risorsa | Admin | Utente normale |
|---|---|---|
| ControlUnit | tutte, incluse con user=null | — (non esposto direttamente) |
| Sensor `GET /api/sensors` | tutti | solo quelli con CU.user = sé stesso |
| DCC `GET /api/dcc` | tutti | solo i propri (dcc.user = sé stesso) |
| Creazione DCC su sensor altrui | consentita | 403 |
| `GET /api/public/sensors` | aperto | aperto |
| `GET /api/public/dcc/{sensorId}` | aperto | aperto |

`Sensor.getOwner()` risale la catena `Sensor -> MeasurementUnit -> ControlUnit -> User` e restituisce null se la CU è unclaimed.

## Endpoint principali
| Metodo | Path | Descrizione |
|---|---|---|
| GET | `/api/dcc` | lista DCC paginata, filtri: sensorId, template, date |
| POST | `/api/dcc` | crea DCC (body: sensorId, name, dccJson) |
| GET | `/api/dcc/{id}` | DCC singolo |
| PUT | `/api/dcc/{id}` | aggiorna nome/sensorId/date/json |
| POST | `/api/dcc/{id}/validate` | firma e carica su S3 (param: fileType) |
| POST | `/api/dcc/{id}/json` | aggiorna solo il JSON |
| POST | `/api/dcc/{id}/publish` | pubblica DCC |
| POST | `/api/dcc/{id}/unpublish` | unpubblica |
| DELETE | `/api/dcc/{id}` | elimina |
| GET | `/api/sensors` | lista sensori disponibili per il DCC |
| GET | `/api/public/sensors` | sensori con DCC pubblicato |
| GET | `/api/public/dcc/{sensorId}` | DCC pubblicato per sensore |
| POST | `/api/dcc/external/validate-xml` | verifica XML esterno |
| POST | `/api/dcc/external/validate-pdf` | verifica PDF esterno |
| GET | `/api/dcc/s3/{id}/{type}` | download da S3 (xml o pdf) |
| GET | `/verify-token` | health check JWT |

## Kafka
- **Ascolta:** `calibrations` → salva `Calibration`; `calibration.request` → elabora e risponde su `calibration.response`
- **Non ascolta** topic di sensor-manager (cu-creation, node-event, mus)
- Le entity ControlUnit / MeasurementUnit / Sensor sono **passive** e vanno popolate manualmente o via script

## Sicurezza
- JWT Keycloak, realm `measurestream-dev` / `measurestream`
- Ruolo admin: `app-admin` in `resource_access.iam1client.roles` → `ROLE_app-admin`
- `/api/public/**` e `/actuator/**` sono aperti senza autenticazione

## Note importanti
- `ddl-auto: update` — Hibernate aggiorna lo schema automaticamente al boot
- Le entity passive (ControlUnit, MeasurementUnit, Sensor) condividono lo stesso DB PostgreSQL con sensor-manager (`SENSORS` schema)
- `MeasurementUnitDto.java` rimane per retrocompatibilità ma non è più usato negli endpoint principali
