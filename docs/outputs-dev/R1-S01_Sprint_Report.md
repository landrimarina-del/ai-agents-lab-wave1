# R1-S01 Sprint Report
Data: 2026-03-05

## Sprint_Selected
- Release: R1
- Sprint: R1-S01
- Fonte: docs/plan/release-target.md

## UserStories_Selected
- US-01: Accesso autenticato/autorizzato
- US-02: Creazione utente
- US-03: Disattivazione/Riattivazione utente
- Dipendenze: US-02 dipende da US-01; US-03 dipende da US-02

## Technical_Task_Plan
- BE-P0/P1
  - Security JWT stateless + filtro auth
  - Lockout 5 tentativi / 15 minuti
  - API: login, create user, deactivate/reactivate
  - Flyway V2 con tabella utenti e seed admin dev
  - Audit eventi su create/deactivate/reactivate
- FE-P0/P1
  - Routing pubblico/protetto
  - Login page + gestione errori 401/403/423
  - Auth service + localStorage + interceptor + guard
  - Users MVP: create user + deactivate/reactivate + blocco self-deactivate
- QA-P0
  - Checklist DoD R1-S01
  - Gherkin acceptance US-01/02/03
  - Smoke script PowerShell R1-S01

## Agent_Assignments
- Backend Dev: COMPLETATO (implementazione API/security/migration/audit)
- Frontend Dev: COMPLETATO (login, protezione rotte, user management MVP)
- QA Agent: COMPLETATO (artefatti test e smoke)

## Definition_of_Done_Check
- build OK: PASS (docker compose up -d --build completato)
- migrations DB OK: PASS (backend avviato con Flyway applicato, login operativo)
- API funzionanti: PASS (`/api/health`, `/api/version`, `/api/auth/login` verificati)
- UI funzionante: PASS (frontend container up e integrato con backend)
- smoke test QA OK: PASS (`qa-r1-s01-smoke.ps1` completato con tutti PASS)

Evidenza runtime ricevuta:
- immagini backend/frontend buildate
- container rise-db healthy
- container rise-backend e rise-frontend recreated/up
- smoke output QA:
  - [PASS] /api/health is UP (status=UP, database=UP)
  - [PASS] /api/version contains keys: application, version
  - [PASS] Admin login returns token
  - Smoke checks completed successfully.

Evidenze statiche:
- Nessun errore editor FE/BE rilevato su workspace.

Artefatti QA:
- scripts/QA_R1-S01_Checklist.md
- scripts/QA_R1-S01_Gherkin.feature
- scripts/qa-r1-s01-smoke.ps1

## Sprint_Report
Stato coordinamento: CHIUSO

Completato nel codice:
- Backend R1-S01 implementato su endpoint e regole principali.
- Frontend R1-S01 implementato in modalità MVP conforme scope.
- QA kit pronto per validazione finale.

Esito finale sprint R1-S01:
- Definition of Done: PASS
- Go/No-Go QA: GO
