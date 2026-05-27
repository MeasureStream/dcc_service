#!/usr/bin/env bash
# =============================================================================
# dcc_service — Test API curl
# Base URL: http://localhost:8080
#
# Dati reali nel DB (sensor-manager / SENSORS):
#   ControlUnit id=1  devEui=8121069293711468000  user=null (unclaimed)
#   ControlUnit id=2  devEui=3240323578579664643  user=null (unclaimed)
#   MeasurementUnit id=1  extendedId=65537  localId=1  model=1  controlUnit=1  user=null
#   Sensor id=1  modelName=accelerometer_lsm6dsm  sensorIndex=1  mu_id=1
#   Sensor id=2  modelName=pressure_ms5837         sensorIndex=2  mu_id=1
#   Sensor id=3  modelName=humidity_hpp845e         sensorIndex=3  mu_id=1
#   Sensor id=4  modelName=ntc_temperature          sensorIndex=4  mu_id=1
#
# Sostituire TOKEN con un JWT valido ottenuto da Keycloak:
#   TOKEN=$(curl -s -X POST "https://auth.christiandellisanti.uk/realms/measurestream-dev/protocol/openid-connect/token" \
#     -d "client_id=gateway&grant_type=password&username=USER&password=PASS" | jq -r .access_token)
# =============================================================================

BASE="http://localhost:8080"
TOKEN="<inserire_token_jwt>"
AUTH="Authorization: Bearer $TOKEN"

echo "============================================================"
echo " HEALTH CHECK"
echo "============================================================"

# Verifica token JWT
curl -s -X GET "$BASE/verify-token" \
  -H "$AUTH" | jq .

echo ""
echo "============================================================"
echo " SENSORS (sola lettura — dati da sensor-manager)"
echo "============================================================"

# Lista tutti i sensori (admin vede tutti, inclusi quelli con CU unclaimed)
curl -s -X GET "$BASE/api/sensors" \
  -H "$AUTH" | jq .

echo ""

# Sensori pubblici (con DCC pubblicato)
curl -s -X GET "$BASE/api/public/sensors" | jq .

echo ""
echo "============================================================"
echo " DCC — CRUD"
echo "============================================================"

# Lista DCC (vuota inizialmente)
curl -s -X GET "$BASE/api/dcc" \
  -H "$AUTH" | jq .

echo ""

# Crea DCC senza sensor linkato (template)
curl -s -X POST "$BASE/api/dcc" \
  -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "DCC Template Test",
    "dccJson": "{}"
  }' | jq .

echo ""

# Crea DCC linkato al sensore 4 (ntc_temperature, mu=1)
# NB: sensor 4 appartiene a CU con user=null -> solo admin può farlo
curl -s -X POST "$BASE/api/dcc" \
  -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{
    "sensorId": "4",
    "name": "DCC NTC Temperature - CU lora-e5",
    "dccJson": "{\"sensorModel\": \"ntc_temperature\"}"
  }' | jq .

echo ""

# Leggi DCC id=1
curl -s -X GET "$BASE/api/dcc/1" \
  -H "$AUTH" | jq .

echo ""

# Aggiorna DCC id=1 — cambia nome e date
curl -s -X PUT "$BASE/api/dcc/1" \
  -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "DCC NTC Temperature aggiornato",
    "calibrationDate": "2026-01-15T10:00:00Z",
    "expirationDate": "2027-01-15T10:00:00Z"
  }' | jq .

echo ""

# Aggiorna solo il JSON del DCC id=1
curl -s -X POST "$BASE/api/dcc/1/json" \
  -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{"sensor": "ntc_temperature", "version": "1.0"}' | jq .

echo ""
echo "============================================================"
echo " DCC — FILTRI"
echo "============================================================"

# Filtra DCC per sensorId=4
curl -s -X GET "$BASE/api/dcc?sensorId=4" \
  -H "$AUTH" | jq .

echo ""

# Lista solo DCC template (sensor=null)
curl -s -X GET "$BASE/api/dcc?template=true" \
  -H "$AUTH" | jq .

echo ""

# Lista DCC con ordinamento e paginazione
curl -s -X GET "$BASE/api/dcc?orderBy=createdAt&orderDir=asc&limit=5&offset=0" \
  -H "$AUTH" | jq .

echo ""
echo "============================================================"
echo " DCC — PUBLISH / UNPUBLISH"
echo "============================================================"

# Pubblica DCC id=1
curl -s -X POST "$BASE/api/dcc/1/publish" \
  -H "$AUTH" | jq .

echo ""

# Verifica DCC pubblicato tramite endpoint pubblico (sensor id=4)
curl -s -X GET "$BASE/api/public/dcc/4" | jq .

echo ""

# Sensori pubblici aggiornati
curl -s -X GET "$BASE/api/public/sensors" | jq .

echo ""

# Unpubblica DCC id=1
curl -s -X POST "$BASE/api/dcc/1/unpublish" \
  -H "$AUTH" | jq .

echo ""
echo "============================================================"
echo " DCC — VALIDATE (richiede gemimeg-backend attivo)"
echo "============================================================"

# Avvia validazione DCC id=1 (firma XML+PDF, upload S3)
# fileType=XML avvia la catena completa
curl -s -X POST "$BASE/api/dcc/1/validate?fileType=XML" \
  -H "$AUTH" | jq .

echo ""
echo "============================================================"
echo " DCC — DOWNLOAD (richiede S3 Garage attivo)"
echo "============================================================"

# Scarica XML firmato
curl -s -X GET "$BASE/api/dcc/1/download/signed-xml" \
  -H "$AUTH" \
  -o /tmp/dcc-1-signed.xml
echo "XML salvato in /tmp/dcc-1-signed.xml"

# Scarica PDF firmato
curl -s -X GET "$BASE/api/dcc/1/download/signed-pdf" \
  -H "$AUTH" \
  -o /tmp/dcc-1-signed.pdf
echo "PDF salvato in /tmp/dcc-1-signed.pdf"

echo ""
echo "============================================================"
echo " DCC — DELETE"
echo "============================================================"

# Crea un DCC temporaneo da eliminare
TMP_ID=$(curl -s -X POST "$BASE/api/dcc" \
  -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{"name": "DCC da eliminare", "dccJson": "{}"}' | jq -r .id)

echo "DCC temporaneo creato con id=$TMP_ID"

# Eliminalo
curl -s -X DELETE "$BASE/api/dcc/$TMP_ID" \
  -H "$AUTH"
echo "DCC id=$TMP_ID eliminato (204 No Content atteso)"

echo ""
echo "============================================================"
echo " VERIFICA ACCESSO UTENTE NORMALE vs ADMIN"
echo "============================================================"
# Nota: per testare un utente normale, ottenere un token senza il ruolo app-admin
# TOKEN_USER=<token_utente_normale>
# I sensori con CU unclaimed (user=null) NON devono essere visibili all'utente normale
# curl -s -X GET "$BASE/api/sensors" -H "Authorization: Bearer $TOKEN_USER" | jq .
# -> atteso: lista vuota (nessuna CU è stata reclamata dall'utente)

echo "Test completati."
