# R1-S03 Coordinator Kickoff
Data: 2026-03-06

## Sprint_Selected
- Release: R1
- Sprint: R1-S03
- Fonte: `docs/plan/release-target.md`

## UserStories_Selected
- US-05: Inserimento nuovo dipendente
- US-06: Aggiornamento e cancellazione logica dipendente
- US-07: Gestione anagrafica negozi/shop

Acceptance Criteria chiave:
- US-05: `employeeId` univoco, vincolo scope Country Manager su BU/paese.
- US-06: update dati dipendente senza perdita storico, disattivazione logica.
- US-07: `shopCode` univoco, CRUD shop con attributi paese/regione.

Dipendenze BA:
- US-05 dipende da US-01 e US-07.
- US-06 dipende da US-05.
- US-07 dipende da US-01.

## Technical_Task_Plan
Vincoli applicati (Architecture/DBA/UX):
- Backend Spring Boot 3.x + JWT + JDBC.
- DB PostgreSQL + Flyway su `infra/db/migrations`.
- RBAC stretto: endpoint protetti per ruolo.
- UX MVP coerente con pagine esistenti Angular.

Task:
1) Migration DB per `shops` e `employees` con soft delete.
2) BE Shops: POST/GET/PUT + validazione `shopCode` univoco.
3) BE Employees: POST/GET/PUT/PATCH deactivate + validazione `employeeId` univoco.
4) Scope enforcement Country Manager su `countryScope` vs shop country.
5) Audit eventi su create/update/deactivate employee e create/update shop.
6) FE route `/app/shops` + `/app/employees`.
7) FE pagine shops/employees con CRUD MVP e gestione errori HTTP.
8) QA smoke/checklist/gherkin dedicati R1-S03.

## Agent_Assignments
- FE Agent: implementazione pages/services/routing shops+employees.
- BE Agent: migration + API shops/employees + scope + audit + security.
- QA Agent: smoke e acceptance R1-S03.

## Definition_of_Done_Check
- build OK: IN CORSO
- migrations DB OK: IN CORSO
- API funzionanti: IN CORSO
- UI funzionante: IN CORSO
- smoke test QA OK: IN CORSO

## Sprint_Report
- Stato coordinamento: AVVIATO
- Prossimo checkpoint: validazione runtime docker + smoke R1-S03.
