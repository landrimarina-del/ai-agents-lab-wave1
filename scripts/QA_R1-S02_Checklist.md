# QA Checklist R1-S02 (US-03 / US-04 / US-08)

## Scope
- R1-S02: stato utente, scope BU per Country Manager, gestione Business Unit
- Ambiente target: locale/dev
- Ruoli coinvolti: Admin, Country Manager

## Comandi esecuzione (PowerShell)
```powershell
docker compose up -d --build
powershell -ExecutionPolicy Bypass -File .\scripts\qa-r1-s02-smoke.ps1 -BaseUrl http://localhost:8080 -AdminEmail admin@rise.local -AdminPassword 'Admin123!'
docker compose down -v
```

## Build & Setup
- [ ] Build FE completata
- [ ] Build BE completata
- [ ] Servizi avviati senza errori bloccanti

## Migration & Data
- [ ] Migrazioni DB applicate
- [ ] Seed admin disponibile (`admin@rise.local`)
- [ ] Dati minimi BU presenti

## API Validation (US-03/04/08)
- [ ] `GET /api/health` => UP
- [ ] `POST /api/auth/login` => token valido
- [ ] `PATCH /api/users/{id}/deactivate` e `PATCH /reactivate` funzionanti
- [ ] `PATCH /api/users/{id}/business-units` aggiorna scope BU
- [ ] CRUD ` /api/business-units` operativo
- [ ] Delete BU con dipendenze bloccato con errore coerente

## UI Validation (Smoke)
- [ ] Admin deattiva/riattiva utente da UI
- [ ] Admin aggiorna BU scope per Country Manager da UI
- [ ] CRUD Business Unit disponibile da UI
- [ ] Messaggio errore su delete BU con dipendenze

## Smoke Regression Summary
- [ ] Smoke script R1-S02 PASS
- [ ] Nessun blocker Sev1/Sev2 aperto su US-03/04/08
- [ ] Evidenze QA raccolte

## Acceptance Coverage
- [ ] US-03: deactivate/reactivate coperto
- [ ] US-04: update BU scope Country Manager coperto
- [ ] US-08: CRUD BU + blocco delete con dipendenze coperto
