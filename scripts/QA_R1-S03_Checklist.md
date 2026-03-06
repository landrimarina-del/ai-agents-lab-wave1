# QA Checklist R1-S03 (US-05 / US-06 / US-07 + CR-01)

## Scope
- US-05 Inserimento dipendente
- US-06 Aggiornamento e disattivazione logica dipendente
- US-07 Gestione anagrafica shop
- CR-01 Menu GLOBAL_ADMIN + CRUD logico country scope + dropdown country in create user

## Comandi
```powershell
docker compose up -d --build
powershell -ExecutionPolicy Bypass -File .\scripts\qa-r1-s03-smoke.ps1 -BaseUrl http://localhost:8080 -AdminEmail admin@rise.local -AdminPasswordPlain 'Admin123!' -BusinessUnitId 1
docker compose down -v
```

## Build & Migration
- [ ] Build Docker OK
- [ ] Flyway migration V5 applicata
- [ ] Flyway migration V6 applicata

## API Validation
- [ ] `POST /api/shops` crea shop con `shopCode` univoco
- [ ] `GET /api/shops` restituisce lista shop
- [ ] `PUT /api/shops/{id}` aggiorna shop
- [ ] `POST /api/employees` crea dipendente con `employeeId` univoco
- [ ] `GET /api/employees` restituisce lista dipendenti
- [ ] `PUT /api/employees/{id}` aggiorna dipendente
- [ ] `PATCH /api/employees/{id}/deactivate` esegue disattivazione logica
- [ ] `POST /api/country-scopes` crea country scope
- [ ] `GET /api/country-scopes` restituisce lista country scopes
- [ ] `PUT /api/country-scopes/{id}` aggiorna country scope
- [ ] `PATCH /api/country-scopes/{id}/deactivate` esegue delete logica

## RBAC / Scope
- [ ] Country Manager non crea dipendente fuori `countryScope`
- [ ] Endpoint shop accessibili solo GLOBAL_ADMIN/SYSTEM_ADMIN
- [ ] Endpoint employee accessibili GLOBAL_ADMIN/COUNTRY_MANAGER
- [ ] Endpoint country scopes accessibili solo GLOBAL_ADMIN

## UI Validation
- [ ] `/app/shops` operativo (list/create/update)
- [ ] `/app/employees` operativo (list/create/update/deactivate)
- [ ] Navigazione da `/app/users` e `/app/business-units` alle nuove sezioni
- [ ] Menu `app-main-menu` visibile a GLOBAL_ADMIN su pagine protette
- [ ] `/app/country-scopes` operativo (list/create/update/deactivate)
- [ ] Form crea utente mostra dropdown country scopes per ruoli non GLOBAL_ADMIN

## Smoke Summary
- [ ] Smoke R1-S03 PASS
- [ ] Nessun blocker Sev1/Sev2
