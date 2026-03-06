# R1-S02 Sprint Report
Data: 2026-03-05

## Sprint_Selected
- Release: R1
- Sprint operativo: R1-S02
- Nota target: file `docs/plan/release-target.md` contiene valore ambiguo (`R1-S02,R1-S02,R1-S03`); usato primo sprint valido.

## UserStories_Selected
- US-03: Disattivazione/Riattivazione utente
- US-04: Modifica permessi/BU associate Country Manager
- US-08: Gestione Business Unit

## Technical_Task_Plan
Eseguito in questa iterazione:
- Backend
  - Modello BU + relazione user↔BU (migration Flyway V4)
  - API CRUD Business Units
  - API PATCH assegnazione BU a Country Manager
  - Audit eventi su BU e update scope utente
  - Security aggiornata per endpoint admin BU
- Frontend
  - Route protetta `/app/business-units`
  - Users UI con azioni contestuali deactivate/reactivate
  - Sezione modifica BU su Country Manager
  - Pagina Business Units (list/create/update/delete)
  - Services separati `users.service.ts` e `business-units.service.ts`

## Agent_Assignments
- Backend Dev: COMPLETATO (iterazione 1)
- Frontend Dev: COMPLETATO (iterazione 1)
- QA Agent: COMPLETATO (artefatti QA R1-S02 prodotti; esecuzione runtime in attesa evidenza)

Artefatti QA disponibili:
- `scripts/qa-r1-s02-smoke.ps1`
- `scripts/QA_R1-S02_Checklist.md`
- `scripts/QA_R1-S02_Gherkin.feature`

## Definition_of_Done_Check
- build OK: PASS
- migrations DB OK: PASS
- API funzionanti: PASS
- UI funzionante: PASS
- smoke test QA OK: PASS

Esito check statico workspace:
- Backend: nessun errore rilevato dal validator.
- Frontend: nessun errore rilevato dal validator dopo riallineamento dipendenze e lockfile.

Stato sicurezza frontend:
- Bonifica dipendenze avviata con upgrade toolchain Angular (fix major richiesti dall'audit).
- `npm install` confermato OK dal team.
- Validazione finale vulnerabilità moderate/high: IN CORSO (snapshot audit post-fix da consolidare come evidenza finale QA).

Comando esecuzione smoke R1-S02:
- `powershell -ExecutionPolicy Bypass -File .\scripts\qa-r1-s02-smoke.ps1 -BaseUrl http://localhost:8080 -AdminEmail admin@rise.local -AdminPassword 'Admin123!'`

## Sprint_Report
Stato coordinamento: CHIUSURA TECNICA RAGGIUNTA

Esito runtime:
- Smoke R1-S02 confermato PASS dal team dopo hardening script.
- Endpoint auth/BU/update scope validati nel run finale.
- Build backend via Docker confermata OK dal team.

Esito sprint:
- US-03: PASS
- US-04: PASS
- US-08: PASS

Go/No-Go:
- QA: GO
- Sprint R1-S02: CLOSED

Prossimi passi operativi:
1) consolidare `audit-after-fix.json` nel pacchetto evidenze QA
2) pianificare kickoff sprint successivo secondo `docs/plan/release-target.md`.
