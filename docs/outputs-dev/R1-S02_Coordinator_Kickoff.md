# R1-S02 Coordinator Kickoff
Data: 2026-03-05

## 1) Sprint_Selected
- Fonte target: `docs/plan/release-target.md`
- Valore rilevato: `R1-S02,R1-S02,R1-S03` (formato ambiguo)
- Assunzione operativa applicata: **R1-S02** (primo sprint valido in sequenza)

## 2) UserStories_Selected
Da BA release plan (`§8`):
- **US-03**: disattivazione/riattivazione utente
- **US-04**: modifica permessi/BU associate a Country Manager
- **US-08**: gestione Business Unit

Acceptance criteria chiave (estratto):
- US-03: disattiva/riattiva account senza perdere storico; blocco auto-disattivazione.
- US-04: aggiunta/rimozione BU a Country Manager con effetto su visibilità e audit.
- US-08: creazione/manutenzione BU; blocco cancellazione in presenza dipendenze.

Dipendenze:
- US-04 dipende da US-02/US-03.
- US-08 dipende da US-01.

## 3) Technical_Task_Plan
Vincoli applicati (Architecture/DBA/UX):
- FE Angular 17, RBAC client-side.
- BE Spring Boot 3.x, security JWT, API REST.
- DB PostgreSQL 15+, Flyway, audit append-only.

Task tecnici (priorità):
1. **BE-P0**: introdurre modello Business Unit (entity + migration + relazioni utente↔BU).
2. **BE-P0**: API CRUD BU (create/update/delete con controllo dipendenze).
3. **BE-P0**: API update scope/permessi Country Manager (US-04) con validazioni ruolo.
4. **BE-P1**: audit eventi modifica scope/permessi e operazioni BU.
5. **FE-P0**: evolvere Users UI da input-id manuale a lista con azioni contestuali.
6. **FE-P0**: UI modifica BU associate per Country Manager (add/remove).
7. **FE-P0**: pagina Business Unit (lista + create/edit/delete + error handling dipendenze).
8. **QA-P0**: suite acceptance US-03/04/08 + smoke regressione auth.

## 4) Agent_Assignments
- **Backend Dev Agent**
  - Ownership: modello BU, API US-04/US-08, audit tecnico.
- **Frontend Dev Agent**
  - Ownership: UI US-03 raffinata + UI US-04 + UI US-08.
- **QA Agent**
  - Ownership: smoke/acceptance/regressione e report GO/NO-GO.

## 5) Definition_of_Done_Check
Stato kickoff:
- build OK: IN CORSO
- migrations DB OK: IN CORSO
- API funzionanti: IN CORSO
- UI funzionante: IN CORSO
- smoke test QA OK: IN CORSO

Gate DoD R1-S02:
- US-03/04/08 PASS in acceptance.
- Nessun bug Critical/High aperto su RBAC/BU.
- Audit presente per operazioni sensibili.

## 6) Sprint_Report
- Coordinamento R1-S02: **AVVIATO**
- Stato iniziale:
  - US-03: parzialmente già coperta (MVP) ma da consolidare UI/API.
  - US-04: gap funzionale significativo.
  - US-08: non implementata.
- Prossimo checkpoint: completamento BE-P0 + FE-P0 e primo run QA acceptance su US-03.
