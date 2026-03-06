# R1-S03 Sprint Report
Data: 2026-03-06

## Sprint_Selected
- Release: R1
- Sprint: R1-S03

## UserStories_Selected
- US-05: Inserimento dipendente
- US-06: Update + disattivazione logica dipendente
- US-07: Gestione anagrafica shop
- US-08: Gestione Business Unit (continuity)
- CR-01 (richiesta utente): menu GLOBAL_ADMIN + CRUD logico Country Scope + dropdown country in creazione utente

## Technical_Task_Plan
Completato in sviluppo:
- Backend:
  - Migration `V5__shops_employees.sql` con tabelle `rise_core.shops` e `rise_core.employees`.
  - Migration `V6__country_scopes_logical_delete.sql` per `rise_core.countries` (is_active, updated_at, deleted_at, unique index case-insensitive).
  - Modulo `shop`: controller/service/repository/dto.
  - Modulo `employee`: controller/service/repository/dto.
  - Modulo `country-scope`: controller/service/repository/dto (`GET/POST/PUT/PATCH deactivate`).
  - Scope check Country Manager su country shop.
  - Audit eventi su create/update/deactivate employee e create/update shop.
  - Audit eventi su create/update/delete logica country scope.
  - Security aggiornata per `/api/shops/**` e `/api/employees/**`.
  - Security aggiornata per `/api/country-scopes/**` (solo `GLOBAL_ADMIN`).
- Frontend:
  - Nuove feature `employees` e `shops` con component/service/html.
  - Nuova feature `country-scopes` con component/service/html.
  - Nuovo componente condiviso `shared/main-menu` visibile solo a `GLOBAL_ADMIN`.
  - Routing `/app/employees` e `/app/shops`.
  - Routing aggiuntivo `/app/country-scopes`.
  - Navigazione pagine protette allineata al menu condiviso.
  - Form creazione utente aggiornato con dropdown multiselect country scope (caricamento da API).
- QA:
  - `scripts/qa-r1-s03-smoke.ps1`
  - `scripts/QA_R1-S03_Checklist.md`
  - `scripts/QA_R1-S03_Gherkin.feature`
  - Smoke/checklist/gherkin estesi per coprire `CR-01` (`country-scopes` + RBAC GLOBAL_ADMIN).

## Agent_Assignments
- FE Agent: COMPLETATO (feature + integrazione menu + dropdown country)
- BE Agent: COMPLETATO (API country scopes + RBAC + migration V6)
- QA Agent: COMPLETATO (smoke runtime eseguito: PASS)

## Definition_of_Done_Check
- build OK: PASS
- migrations DB OK: PASS (`V6` applicata)
- API funzionanti: PASS
- UI funzionante: PASS
- smoke test QA OK: PASS

Check statico workspace:
- Backend: PASS (no compile errors editor)
- Frontend: PASS (no compile errors editor)
- Residuo non bloccante: warning script PowerShell su parametro password in `scripts/qa-r1-s03-smoke.ps1`

## Sprint_Report
Stato: CHIUSO

Evidenze implementazione:
- Delta CR-01 implementato su backend, frontend e migration DB.
- Delta QA CR-01 implementato su smoke/checklist/gherkin.
- Nessun errore statico FE/BE nel workspace sui file applicativi.

Evidenze runtime:
- Smoke R1-S03 esteso (US-05/06/07 + CR-01): PASS.
- Validati endpoint shops/employees/country-scopes e RBAC GLOBAL_ADMIN.

Go/No-Go:
- QA: GO
- Sprint R1-S03: CLOSED

Comandi ri-esecuzione (validazione finale):
1) `docker compose up -d --build`
2) `powershell -ExecutionPolicy Bypass -File .\\scripts\\qa-r1-s03-smoke.ps1 -BaseUrl http://localhost:8080 -AdminEmail admin@rise.local -AdminPasswordPlain 'Admin123!' -BusinessUnitId 1`
3) Verifica manuale UI: login come `GLOBAL_ADMIN`, controllo menu globale, pagina `/app/country-scopes`, form creazione utente con dropdown country scopes.
