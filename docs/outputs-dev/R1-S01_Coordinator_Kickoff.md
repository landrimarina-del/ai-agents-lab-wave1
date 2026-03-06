# Coordinamento Sviluppo – Kickoff
Data: 2026-03-05

## 1) Sprint_Selected
- Release target: **R1**
- Sprint target: **R1-S01**
- Fonte: `docs/plan/release-target.md`

## 2) UserStories_Selected
User Stories selezionate da BA (R1-S01):
- **US-01** – Accesso autenticato e autorizzato (login, token/sessione, gestione errori)
- **US-02** – Creazione nuovo utente (vincoli ruolo/BU, email unica)
- **US-03** – Disattivazione/Riattivazione utente (no self-deactivate, audit)

Acceptance Criteria chiave (estratto operativo):
- Login riuscito → accesso dashboard coerente col ruolo.
- Login fallito ripetuto → blocco temporaneo account.
- Utente disattivato → login negato.
- Creazione utente con email duplicata → errore bloccante.
- Country Manager senza BU → salvataggio bloccato.
- Disattivazione/riattivazione mantiene storico operativo.
- Blocco auto-disattivazione admin.

Dipendenze funzionali:
- US-02 dipende da US-01.
- US-03 dipende da US-02.

## 3) Technical_Task_Plan
Vincoli tecnici applicati (Architecture + DBA + UX):
- FE: Angular 17 SPA, RBAC client-side, UX desktop-first.
- BE: Spring Boot 3.x + JWT/OIDC boundary, API REST.
- DB: PostgreSQL 15+, schema `rise_core` + `rise_audit`, Flyway versioned migrations.
- Sicurezza: audit immutabile, segregazione ruoli, campi utente con stato attivo/disattivo.

Task tecnici (ordine di esecuzione):
1. **BE-P0**: Modellazione utenti e sicurezza base
   - Migration tabelle utenti/ruoli/scope BU + campi lock (`failed_attempts`, `locked_until`, `is_active`).
   - Security config + JWT auth flow minimale.
2. **BE-P1**: API R1-S01
   - `POST /api/auth/login`
   - `POST /api/users`
   - `PATCH /api/users/{id}/deactivate`
   - `PATCH /api/users/{id}/reactivate`
   - Audit eventi lifecycle utente.
3. **FE-P0**: Auth shell
   - Login page, AuthService, token storage, interceptor, route guard, redirect per ruolo.
4. **FE-P1**: User management base
   - Lista utenti + create user form con validazioni AC.
   - Azioni disattiva/riattiva con blocco self-deactivate.
5. **QA-P0**: Test acceptance + regression
   - Suite smoke + casi Gherkin US-01/02/03 + evidenze.

## 4) Agent_Assignments
- **Backend Dev Agent**
  - Owner: US-01 (core auth), US-02 (create user API), US-03 (deactivate/reactivate API)
  - Deliverable: migration, security config, endpoint + test integrazione.
- **Frontend Dev Agent**
  - Owner: UI login, RBAC guard/interceptor, user management UI.
  - Deliverable: pagine/flow completi + validazioni AC lato client.
- **QA Agent**
  - Owner: piani test, automazione smoke/acceptance, report GO/NO-GO.
  - Deliverable: checklist DoD + evidenze test.

## 5) Definition_of_Done_Check
Stato corrente (kickoff):
- build OK: **IN CORSO**
- migrations DB OK: **IN CORSO**
- API funzionanti: **IN CORSO**
- UI funzionante: **IN CORSO**
- smoke test QA OK: **IN CORSO**

Gate di chiusura sprint:
- BE e FE allineati ai contratti API.
- Tutti AC US-01/02/03 verificati (PASS o BLOCKED con ticket approvato).
- Nessun bug Critical/High aperto sui flussi target.

## 6) Sprint_Report
Esito avvio coordinamento: **AVVIATO**

Sintesi:
- Sprint e scope R1-S01 identificati e confermati.
- User Stories target e dipendenze estratte.
- Task plan tecnico derivato da BA/Architecture/DBA/UX.
- Assegnazioni FE/BE/QA emesse.
- DoD inizializzato con stato operativo **IN CORSO**.

Prossimo checkpoint coordinamento:
- allineamento su implementazione BE-P0/FE-P0,
- validazione integration FE↔BE,
- primo passaggio QA smoke + acceptance US-01.
