````chatagent
---
name: Architect
description: RISE Spending Effectiveness — Documento di Architettura v1.0.0
tools: ['read', 'codebase', 'usages']

---

# RISE Spending Effectiveness — Documento di Architettura

> **Versione:** 1.0.0 — **Data:** 2026-03-04
> **Autore:** Architect Agent (GitHub Copilot — Claude Sonnet 4.6)
> **Stato:** APPROVATO

---

## §1 Contesto e Vincoli

### Assunzioni Architetturali

| ID | Assunzione |
|----|-----------|
| **A-01** | Il sistema è una piattaforma B2B enterprise multi-paese; ogni utente appartiene a uno o più paesi e l'accesso ai dati è sempre filtrato per paese (country scoping obbligatorio). |
| **A-02** | L'Identity Provider esterno (es. Azure AD / Okta) gestisce l'autenticazione via OIDC/PKCE; la piattaforma non conserva password utente. |
| **A-03** | I dati di retribuzione variabile costituiscono dati personali ai sensi del GDPR: i campi PII (full_name, email) sono cifrati a livello di colonna con pgcrypto (BYTEA); il masking è applicato in export per ruoli non autorizzati. |
| **A-04** | Il database PostgreSQL 15+ è l'unica fonte di verità; lo schema rise_audit è append-only e non modificabile da applicazione. |
| **A-05** | Spring Batch gestisce esclusivamente i job di import; ogni esecuzione è tracciata in spring_batch schema (auto-gestito) e in rise_core.import_logs. |
| **A-06** | Le Materialized View (mv_kpi_monthly) vengono aggiornate in modo asincrono dopo ogni import e in finestra notturna; i client leggono sempre la MV, mai la tabella base. |
| **A-07** | Il frontend Angular 17 utilizza esclusivamente componenti standalone, senza NgModule; lo stato globale è gestito da NgRx Signal Store. |
| **A-08** | La comunicazione di progresso import avviene via Server-Sent Events (SSE); non è previsto WebSocket. |
| **A-09** | Il deployment avviene su Kubernetes (almeno 2 repliche per tier applicativo); la scalabilità orizzontale è limitata ai tier stateless (frontend, backend API); il database è single-primary con read-replica. |
| **A-10** | Tutti i secret (credenziali DB, client_secret IdP, chiavi pgcrypto) sono iniettati a runtime da un Vault (es. HashiCorp Vault / Azure Key Vault); non esistono secret in chiaro nel repository. |

---

## §2 Diagrammi C4 ASCII

### 2.1 System Context

```
+-------------------------------------------------------------------------+
|                        SISTEMA RISE                                     |
|                                                                         |
|   +-------------+     HTTPS/OIDC    +------------------------------+   |
|   | Global Admin| ─────────────────>|                              |   |
|   +-------------+                   |   RISE Spending              |   |
|   +--------------+    HTTPS/OIDC    |   Effectiveness              |   |
|   |Country Mgr   | ─────────────────>   (Piattaforma Web)          |   |
|   +--------------+                  |                              |   |
|   +--------------+    HTTPS/OIDC    |                              |   |
|   | System Admin | ─────────────────>                              |   |
|   +--------------+                  +──────────────+───────────────+   |
|                                                    |                   |
|          +─────────────────────────────────────────+                   |
|          |                    |                    |                   |
|          v                    v                    v                   |
|   +─────────────+   +──────────────+   +────────────────────+         |
|   |  Identity   |   |  PostgreSQL  |   |   File Storage     |         |
|   |  Provider   |   |  15+ (PaaS)  |   |   (XLSX/CSV input) |         |
|   | (OIDC/PKCE) |   |              |   |                    |         |
|   +─────────────+   +──────────────+   +────────────────────+         |
+─────────────────────────────────────────────────────────────────────────+
```

### 2.2 Container Diagram

```
+──────────────────────────────────────────────────────────────────────────────+
|  RISE Spending Effectiveness                                                 |
|                                                                              |
|  +──────────────────────────+         +───────────────────────────────────+ |
|  |   Angular 17 SPA         |  REST   |   Spring Boot 3.x API Server      | |
|  |  (Nginx / CDN)           | ──────> |   (JVM 21, porta 8080)            | |
|  |                          |  SSE    |                                   | |
|  |  - NgRx Signal Store     | <────── |   - Spring MVC (REST)             | |
|  |  - Angular Material      |         |   - Spring Security 6 OIDC        | |
|  |  - ECharts               |         |   - Spring Batch 5                | |
|  |  - SheetJS               |         |   - JPA/Hibernate 6               | |
|  +──────────────────────────+         |   - Micrometer/Prometheus         | |
|                                       +─────────────+─────────────────────+ |
|                                                     |                       |
|                    +────────────────────────────────+                       |
|                    |                    |           |                       |
|                    v                    v           v                       |
|         +──────────────────+  +──────────────+  +──────────────+           |
|         |  rise_core       |  |  rise_audit  |  | spring_batch |           |
|         |  (schema PG)     |  |  (schema PG) |  |  (schema PG) |           |
|         |  dati business   |  |  append-only |  |  auto-gestito|           |
|         +──────────────────+  +──────────────+  +──────────────+           |
+──────────────────────────────────────────────────────────────────────────────+
```

### 2.3 Component Diagram — Spring Boot Internals

```
+─────────────────────────────────────────────────────────────────────────────+
|  Spring Boot API Server                                                     |
|                                                                             |
|  +─────────────+  +─────────────+  +──────────────+  +──────────────────+  |
|  |  Security   |  |   REST      |  |   Service    |  |  Spring Batch    |  |
|  |  Filter     |  | Controllers |  |   Layer      |  |  Job Config      |  |
|  |  Chain      |  |             |  |              |  |                  |  |
|  | - OIDC      |  | - Master    |  | - KpiService |  | - FileItemReader |  |
|  | - JWT       |  |   DataCtrl  |  | - ImportSvc  |  | - MappingProc.  |  |
|  | - CORS      |  | - ImportCtrl|  | - ExportSvc  |  | - ValidationP.  |  |
|  | - CSRF off  |  | - KpiCtrl   |  | - AuditSvc   |  | - DuplicateP.   |  |
|  |             |  | - ExportCtrl|  |              |  | - RecordWriter  |  |
|  |  Scope      |  | - AdminCtrl |  |  Country     |  | - LogTasklet    |  |
|  |  Injector   |  |             |  |  ScopeService|  |                  |  |
|  +─────────────+  +─────────────+  +──────+───────+  +──────────────────+  |
|                                           |                                 |
|                        +──────────────────+                                 |
|                        |                  |                                 |
|                        v                  v                                 |
|              +──────────────────+  +────────────────+                       |
|              |  JPA Repository  |  |  Audit Writer  |                       |
|              |  (rise_core)     |  |  (rise_audit)  |                       |
|              +──────────────────+  +────────────────+                       |
+─────────────────────────────────────────────────────────────────────────────+
```

---

## §3 Frontend Angular

### 3.1 Tech Stack

| Libreria / Tool | Versione | Utilizzo |
|----------------|---------|---------|
| Angular | 17.x | Framework SPA, componenti standalone |
| NgRx Signal Store | 17.x | State management reattivo |
| Angular Material | 17.x | UI component library |
| Apache ECharts (ngx-echarts) | 18.x | Grafici KPI e benchmark |
| SheetJS (xlsx) | 0.20.x | Preview e download XLSX lato client |
| Angular CDK | 17.x | Drag & drop, overlay, a11y |
| TypeScript | 5.3.x | Linguaggio principale |
| RxJS | 7.8.x | Programmazione reattiva, SSE handling |
| Tailwind CSS | 3.4.x | Utility CSS (affianca Material) |
| Jest | 29.x | Unit testing |
| Cypress | 13.x | E2E testing |
| ESLint + Prettier | — | Qualità codice |

### 3.2 Struttura Directory `src/app/`

```
src/app/
├── core/
│   ├── auth/
│   │   ├── auth.service.ts
│   │   ├── auth.guard.ts
│   │   ├── oidc-callback.component.ts
│   │   └── token-interceptor.ts
│   ├── http/
│   │   ├── api.service.ts
│   │   └── error-interceptor.ts
│   ├── scope/
│   │   └── country-scope.service.ts
│   └── audit/
│       └── audit-log.service.ts
│
├── shared/
│   ├── components/
│   │   ├── data-table/
│   │   ├── kpi-card/
│   │   ├── chart-wrapper/
│   │   ├── file-upload/
│   │   └── confirm-dialog/
│   ├── pipes/
│   │   ├── mask-pii.pipe.ts
│   │   └── format-currency.pipe.ts
│   └── models/
│       ├── api-error.model.ts
│       ├── employee.model.ts
│       ├── kpi.model.ts
│       └── import-job.model.ts
│
├── features/
│   ├── dashboard/
│   │   ├── dashboard.component.ts
│   │   ├── dashboard.store.ts
│   │   └── widgets/
│   │       ├── kpi-summary-widget.component.ts
│   │       └── trend-chart-widget.component.ts
│   │
│   ├── import/
│   │   ├── import-wizard/
│   │   │   ├── import-wizard.component.ts
│   │   │   ├── import-wizard.store.ts
│   │   │   ├── steps/
│   │   │   │   ├── step-upload.component.ts
│   │   │   │   ├── step-mapping.component.ts
│   │   │   │   ├── step-validation.component.ts
│   │   │   │   ├── step-options.component.ts
│   │   │   │   └── step-progress.component.ts
│   │   │   └── import-wizard.routes.ts
│   │   └── import-history/
│   │       ├── import-history.component.ts
│   │       └── import-detail.component.ts
│   │
│   ├── kpi/
│   │   ├── kpi-explorer/
│   │   │   ├── kpi-explorer.component.ts
│   │   │   └── kpi-explorer.store.ts
│   │   └── benchmark/
│   │       ├── benchmark.component.ts
│   │       └── benchmark.store.ts
│   │
│   ├── master-data/
│   │   ├── employees/
│   │   ├── shops/
│   │   ├── countries/
│   │   └── templates/
│   │
│   ├── export/
│   │   ├── export-builder.component.ts
│   │   └── export.store.ts
│   │
│   └── admin/
│       ├── user-management/
│       ├── role-management/
│       └── system-config/
│
├── app.config.ts
├── app.routes.ts
└── app.component.ts
```

### 3.3 State Machine Wizard Import (ASCII)

```
              +──────────+
        ──────>  IDLE    |<──────────────────────+
              +────+─────+                       |
                   |  avvia wizard                |
                   v                             |  annulla / reset
              +──────────+                       |
              |  UPLOAD  |                       |
              +────+─────+                       |
                   |  file selezionato            |
                   v                             |
              +──────────+                       |
              | MAPPING  |                       |
              +────+─────+                       |
                   |  mapping confermato          |
                   v                             |
              +────────────+                     |
              | VALIDATION |                     |
              |  PREVIEW   |                     |
              +────+───────+                     |
                   |  nessun errore bloccante     |
                   v                             |
              +──────────+                       |
              | OPTIONS  | (SKIP | OVERWRITE)    |
              +────+─────+                       |
                   |  conferma esecuzione         |
                   v                             |
              +──────────+                       |
              | PROGRESS | <── SSE stream ──      |
              +────+─────+                       |
          +────────+────────+                    |
          v                 v                    |
    +──────────+      +──────────+               |
    | SUCCESS  |      |  ERROR   |               |
    +──────────+      +──────────+               |
          |                 |                    |
          +─────────────────+────────────────────+
```

---

## §4 Backend Spring Boot

### 4.1 Tech Stack

| Libreria / Tool | Versione | Utilizzo |
|----------------|---------|---------|
| Java | 21 (LTS) | Linguaggio principale, virtual threads |
| Spring Boot | 3.3.x | Framework applicativo |
| Spring MVC | 6.1.x | REST API, SSE |
| Spring Security | 6.3.x | OIDC resource server, RBAC |
| Spring Batch | 5.1.x | Pipeline ETL import |
| Spring Data JPA | 3.3.x | Accesso dati, Specification pattern |
| Hibernate | 6.5.x | ORM, column-level encryption wrapper |
| Flyway | 10.x | Migrazioni schema DB |
| Apache POI | 5.3.x | Parsing XLSX in Batch |
| OpenCSV | 5.9.x | Parsing CSV in Batch |
| Micrometer + Prometheus | 1.13.x | Metriche applicative |
| Logback + ELK | — | Logging strutturato JSON |
| pgcrypto | (PG ext.) | Cifratura colonne PII |
| Testcontainers | 1.19.x | Test integrazione con PG reale |
| JUnit 5 + Mockito | 5.x / 5.x | Unit testing |

### 4.2 Pipeline Spring Batch — Tutti gli Step

```
+──────────────────────────────────────────────────────────────────────────+
|  JOB: importMonthlyPerformance                                           |
|                                                                          |
|  +─────────────────────────────────────────────────────────────────+    |
|  |  STEP 1 — fileReadStep                                          |    |
|  |  Reader: MultiResourceItemReader                                |    |
|  |    +─> XlsxItemReader (Apache POI)   [se estensione .xlsx]     |    |
|  |    +─> CsvItemReader  (OpenCSV)      [se estensione .csv]      |    |
|  |  Output: RawRowDto (Map<String,String>)                         |    |
|  +──────────────────────────+──────────────────────────────────────+    |
|                             |                                           |
|                             v                                           |
|  +─────────────────────────────────────────────────────────────────+    |
|  |  STEP 2 — mappingTransformStep                                  |    |
|  |  Processor: MappingTransformProcessor                           |    |
|  |    - Legge column_mappings JSONB dal template                   |    |
|  |    - Applica transformation_rules (date format, trim, ecc.)     |    |
|  |  Output: MappedRecordDto                                        |    |
|  +──────────────────────────+──────────────────────────────────────+    |
|                             |                                           |
|                             v                                           |
|  +─────────────────────────────────────────────────────────────────+    |
|  |  STEP 3 — validationStep                                        |    |
|  |  Processor: ValidationProcessor                                 |    |
|  |    - Campi obbligatori (employee_id, shop_id, record_month ...) |    |
|  |    - Controllo tipo (numerici, date ISO-8601)                   |    |
|  |    - Range date (non future, non >24 mesi passati)              |    |
|  |    - Cross-reference oracle_hcm_id -> employees table           |    |
|  |  Codici errore: VAL-001 ... VAL-009                             |    |
|  |  Record invalidi -> import_log_errors (no interruzione job)     |    |
|  +──────────────────────────+──────────────────────────────────────+    |
|                             |                                           |
|                             v                                           |
|  +─────────────────────────────────────────────────────────────────+    |
|  |  STEP 4 — duplicateDetectionStep                                |    |
|  |  Processor: DuplicateDetectionProcessor                         |    |
|  |    Chiave univoca: employee_id + shop_id +                      |    |
|  |                    record_month + record_year                   |    |
|  |    Strategia SKIP    -> scarta il duplicato, log warning        |    |
|  |    Strategia OVERWRITE -> marca record come da sovrascrivere    |    |
|  +──────────────────────────+──────────────────────────────────────+    |
|                             |                                           |
|                             v                                           |
|  +─────────────────────────────────────────────────────────────────+    |
|  |  STEP 5 — writeStep  (chunk-size: 500)                          |    |
|  |  Writer: MonthlyPerformanceRecordWriter                         |    |
|  |    - UPSERT su monthly_performance_records                      |    |
|  |    - Aggiorna import_log_rows (contatori ok/warn/err)           |    |
|  |    - Pubblica eventi SSE via SseEmitter pool                    |    |
|  +──────────────────────────+──────────────────────────────────────+    |
|                             |                                           |
|                             v                                           |
|  +─────────────────────────────────────────────────────────────────+    |
|  |  STEP 6 — closingTasklet                                        |    |
|  |  Tasklet: ImportLogClosingTasklet                               |    |
|  |    - Imposta import_logs.status = COMPLETED / FAILED            |    |
|  |    - Avvia refresh asincrono mv_kpi_monthly (Thread pool)       |    |
|  |    - Emette evento SSE DONE/ERROR                               |    |
|  +─────────────────────────────────────────────────────────────────+    |
+──────────────────────────────────────────────────────────────────────────+
```

---

## §5 Contratti API

### §5.1 URL Base e Versionamento

- **URL base:** `https://<host>/api/v1`
- **Versionamento:** URL path (`/v1`); la versione major cambia solo in caso di breaking change; deprecazione con header `Sunset`.
- **Content-Type:** `application/json` (request e response), `text/event-stream` (SSE), `application/octet-stream` (download file).
- **Autenticazione:** `Authorization: Bearer <JWT>` su ogni endpoint tranne `/auth/callback`.

---

### §5.2 Endpoint Master Data

| Metodo | Path | Descrizione | Ruolo Minimo |
|--------|------|-------------|-------------|
| GET | `/master-data/employees` | Lista dipendenti (paginata, filtrata per paese) | Country Manager |
| GET | `/master-data/employees/{id}` | Dettaglio dipendente | Country Manager |
| POST | `/master-data/employees` | Crea dipendente | Global Admin |
| PUT | `/master-data/employees/{id}` | Aggiorna dipendente | Global Admin |
| DELETE | `/master-data/employees/{id}` | Disattiva dipendente (soft delete) | Global Admin |
| GET | `/master-data/shops` | Lista punti vendita per paese | Country Manager |
| GET | `/master-data/shops/{id}` | Dettaglio punto vendita | Country Manager |
| POST | `/master-data/shops` | Crea punto vendita | Global Admin |
| PUT | `/master-data/shops/{id}` | Aggiorna punto vendita | Global Admin |
| GET | `/master-data/countries` | Lista paesi abilitati | Country Manager |
| GET | `/master-data/import-templates` | Lista template di import disponibili | Country Manager |
| POST | `/master-data/import-templates` | Crea / aggiorna template | Global Admin |
| GET | `/master-data/import-templates/{id}` | Dettaglio template (column_mappings, rules) | Country Manager |

---

### §5.3 Endpoint Import

| Metodo | Path | Descrizione | Ruolo Minimo |
|--------|------|-------------|-------------|
| POST | `/import/upload` | Carica file (multipart/form-data) — restituisce fileId temporaneo | Country Manager |
| POST | `/import/execute` | Avvia job import (JSON body) | Country Manager |
| GET | `/import/status/{jobExecutionId}` | Stato corrente job (polling o SSE) | Country Manager |
| GET | `/import/progress/{jobExecutionId}` | SSE stream progresso real-time | Country Manager |
| GET | `/import/logs` | Lista log di import per paese e periodo | Country Manager |
| GET | `/import/logs/{logId}` | Dettaglio log con righe ok/warn/err | Country Manager |
| GET | `/import/logs/{logId}/errors` | Lista errori di validazione per log | Country Manager |
| DELETE | `/import/logs/{logId}` | Annulla / rollback import (se permesso) | Global Admin |

#### Esempio — POST `/api/v1/import/execute`

**Request:**
```json
{
  "fileId": "tmp-uuid-1234",
  "templateId": 7,
  "countryCode": "IT",
  "recordMonth": 3,
  "recordYear": 2026,
  "duplicateStrategy": "OVERWRITE",
  "dryRun": false
}
```

**Response 202 Accepted:**
```json
{
  "jobExecutionId": 982,
  "importLogId": 441,
  "status": "STARTED",
  "startedAt": "2026-03-04T10:15:30Z",
  "sseUrl": "/api/v1/import/progress/982",
  "message": "Job avviato. Monitorare l'avanzamento tramite SSE."
}
```

#### Esempio — GET `/api/v1/import/status/{jobExecutionId}`

**Response 200 OK:**
```json
{
  "jobExecutionId": 982,
  "importLogId": 441,
  "status": "COMPLETED",
  "totalRows": 1500,
  "processedRows": 1500,
  "validRows": 1487,
  "warningRows": 8,
  "errorRows": 5,
  "skippedRows": 0,
  "startedAt": "2026-03-04T10:15:30Z",
  "completedAt": "2026-03-04T10:16:45Z",
  "durationMs": 75000
}
```

---

### §5.4 Endpoint KPI

| Metodo | Path | Descrizione | Ruolo Minimo |
|--------|------|-------------|-------------|
| GET | `/kpi/summary` | Riepilogo KPI mensili per paese/periodo | Country Manager |
| GET | `/kpi/trends` | Serie storica KPI (ultimi N mesi) | Country Manager |
| GET | `/kpi/benchmark` | Confronto cross-paese (solo paesi autorizzati) | Global Admin |
| GET | `/kpi/breakdown` | KPI disaggregati per shop/cluster | Country Manager |
| GET | `/kpi/export-preview` | Anteprima dati KPI prima dell'export | Country Manager |

#### Esempio — GET `/api/v1/kpi/summary?countryCode=IT&month=3&year=2026`

**Response 200 OK:**
```json
{
  "countryCode": "IT",
  "period": "2026-03",
  "refreshedAt": "2026-03-04T02:00:00Z",
  "kpis": {
    "totalVariableRetribution": 2450000.00,
    "avgVariablePerEmployee": 1633.33,
    "totalEmployees": 1500,
    "avgSalesPerShop": 87500.00,
    "topPerformingShops": [
      { "shopId": "MI-001", "shopName": "Milano Centro", "score": 98.7 },
      { "shopId": "RM-003", "shopName": "Roma Est", "score": 96.2 }
    ],
    "kpiAchievementRate": 0.87,
    "complianceRate": 0.99
  }
}
```

---

### §5.5 Endpoint Export

| Metodo | Path | Descrizione | Ruolo Minimo |
|--------|------|-------------|-------------|
| POST | `/export/xlsx` | Genera e scarica report XLSX | Country Manager |
| POST | `/export/csv` | Genera e scarica report CSV | Country Manager |
| GET | `/export/templates` | Lista template di export disponibili | Country Manager |
| POST | `/export/schedule` | Pianifica export ricorrente | Global Admin |
| GET | `/export/history` | Storico export generati per utente | Country Manager |

---

### §5.6 Endpoint Admin

| Metodo | Path | Descrizione | Ruolo Minimo |
|--------|------|-------------|-------------|
| GET | `/admin/users` | Lista utenti della piattaforma | System Admin |
| POST | `/admin/users/{userId}/roles` | Assegna / rimuovi ruoli | System Admin |
| GET | `/admin/audit-logs` | Consultazione log di audit immutabili | System Admin |
| GET | `/admin/system/health` | Health check esteso (DB, batch, cache) | System Admin |
| POST | `/admin/kpi/refresh` | Forza refresh manuale mv_kpi_monthly | System Admin |
| GET | `/admin/batch/jobs` | Lista job Spring Batch (history) | System Admin |
| POST | `/admin/batch/jobs/{jobId}/restart` | Riavvia job fallito | System Admin |

---

### §5.7 Modello Errore Standard

```json
{
  "timestamp": "2026-03-04T10:20:00Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "code": "VAL-003",
  "message": "Il campo record_month deve essere compreso tra 1 e 12.",
  "path": "/api/v1/import/execute",
  "requestId": "req-a1b2c3d4-e5f6"
}
```

| Campo | Tipo | Descrizione |
|-------|------|-------------|
| `timestamp` | ISO-8601 UTC | Momento dell'errore |
| `status` | integer | HTTP status code |
| `error` | string | Categoria HTTP dell'errore |
| `code` | string | Codice applicativo (es. VAL-003, AUTH-001) |
| `message` | string | Messaggio leggibile; può essere localizzato |
| `path` | string | Path della richiesta che ha generato l'errore |
| `requestId` | string | UUID della richiesta (correlabile nei log) |

---

## §6 Sicurezza

### §6.1 Flow OIDC/PKCE (Sequenza ASCII)

```
 Browser/SPA          Spring Boot           Identity Provider
     |                    |                       |
     |  1. avvia login     |                       |
     |───────────────────>|                       |
     |                    | redirect_uri +         |
     |  2. redirect        | code_challenge         |
     |<───────────────────|                       |
     |                    |                       |
     |  3. Authorization Request (PKCE)           |
     |──────────────────────────────────────────>|
     |                    |                       |
     |  4. Login utente   |                       |
     |<─────────────────────────────────────────>|
     |                    |                       |
     |  5. Authorization Code (redirect)          |
     |<──────────────────────────────────────────|
     |                    |                       |
     |  6. Token Request (code + code_verifier)   |
     |──────────────────────────────────────────>|
     |                    |                       |
     |  7. Access + ID + Refresh Token            |
     |<──────────────────────────────────────────|
     |                    |                       |
     |  8. API Call: Bearer <access_token>        |
     |───────────────────>|                       |
     |                    | 9. verifica JWT (JWKS) |
     |                    |──────────────────────>|
     |                    | 10. public key         |
     |                    |<──────────────────────|
     |                    | 11. token valido        |
     |  12. Response      |                       |
     |<───────────────────|                       |
```

### §6.2 Matrice RBAC

| Funzionalità | Global Admin | Country Manager | System Admin |
|-------------|:-----------:|:---------------:|:------------:|
| Visualizza dati tutti i paesi | SI | NO (solo paese assegnato) | SI (read-only) |
| Importa dati | SI | SI | NO |
| Annulla / rollback import | SI | NO | NO |
| Crea / modifica master data | SI | NO | NO |
| Visualizza KPI e benchmark | SI | SI (paese) | SI |
| Esporta dati (completo, no masking) | SI | NO | NO |
| Esporta dati (masking PII) | SI | SI | NO |
| Gestione utenti e ruoli | NO | NO | SI |
| Consulta audit log | SI | NO | SI |
| Refresh manuale MV KPI | NO | NO | SI |
| Configurazione template import | SI | NO | NO |
| Restart job Batch falliti | NO | NO | SI |

### §6.3 Country Scoping

Il `SecurityContextScopeService` implementa il pattern **Specification** di Spring Data JPA. Ad ogni richiesta autenticata:

1. Estrae il claim `countries` dal JWT (lista di country codes).
2. Costruisce un `Predicate` JPA: `root.get("countryCode").in(allowedCountries)`.
3. Il predicato è iniettato automaticamente in ogni `JpaSpecificationExecutor` tramite un `AOP @Around` applicato a tutti i metodi di servizio annotati con `@CountryScoped`.
4. Il `Global Admin` riceve un predicato `1=1` (nessun filtro).
5. Qualsiasi dato restituito senza il predicato attivo genera un'eccezione di sicurezza (`CountryScopeViolationException`).

### §6.4 Controlli GDPR

| Controllo | Implementazione |
|-----------|----------------|
| Cifratura PII a riposo | `pgcrypto`: `full_name_enc BYTEA = pgp_sym_encrypt(...)`, `email_enc BYTEA = pgp_sym_encrypt(...)` |
| Masking in export | `ExportMaskingFilter`: sostituisce PII con "ANONIMIZZATO" per Country Manager in output XLSX/CSV |
| Diritto alla cancellazione | API `DELETE /master-data/employees/{id}`: soft-delete + cifratura nulla dei campi PII |
| Audit immutabile | Schema `rise_audit`: insert-only via trigger PG; nessun UPDATE/DELETE concesso al ruolo applicativo |
| Minimizzazione dati | Le API KPI restituiscono aggregati; i dati individuali sono accessibili solo ai ruoli autorizzati |
| Retention policy | Job schedulato notturno: anonimizza record con `record_date < NOW() - INTERVAL '5 years'` |
| Trasferimento dati cross-paese | Il benchmark cross-paese espone solo medie aggregate; nessun record individuale cross-country |

---

## §7 NFR (Non-Functional Requirements)

| NFR | Target | Implementazione |
|-----|--------|----------------|
| **Performance API** | P95 < 300 ms per endpoint singolo | Connection pool HikariCP (max 20), query plan cache, indici su chiavi di filtro |
| **Throughput Import** | >= 5.000 record/minuto | Chunk size 500, virtual threads Java 21, UPSERT batch PostgreSQL |
| **Disponibilita** | 99,5% mensile (escluse finestre di manutenzione pianificate) | Deployment Kubernetes multi-replica, health probe Liveness/Readiness |
| **Refresh MV KPI** | Completato entro 5 minuti da fine import | `REFRESH MATERIALIZED VIEW CONCURRENTLY` su thread pool dedicato |
| **Scalabilita Orizzontale** | Auto-scaling 2-8 pod backend in base a CPU > 70% | HPA Kubernetes + stateless design (JWT stateless, nessuna sessione server) |
| **Osservabilita** | Full tracing distribuito, metriche Prometheus, log strutturati | Micrometer + OpenTelemetry, Logback JSON, Grafana dashboard |
| **Sicurezza** | Zero credenziali hardcoded; rotazione secret < 24h | HashiCorp Vault + Spring Cloud Vault; secret TTL configurabile |
| **Resilienza Batch** | Job riavviabile in caso di errore parziale | Spring Batch `restart=true`, checkpoint su DB; `skip-limit` configurabile per record invalidi |
| **Time-to-Recovery** | RTO < 1h, RPO < 15min | Backup continuo WAL PostgreSQL, replica sincrona standby, runbook automatizzato |

---

## §8 Architecture Decision Records (ADR)

### ADR-001 — Adozione di Angular Standalone Components (senza NgModules)

**Contesto:** Angular 17 promuove i componenti standalone come approccio raccomandato. Il team valuta se mantenere NgModules per compatibilità o migrare completamente.

**Alternative:**
1. Mantenere NgModules (approccio tradizionale).
2. Componenti standalone con lazy routing (approccio raccomandato Angular 17+).
3. Migrazione graduale (hybrid).

**Decisione:** Opzione 2 — componenti standalone puri.

**Trade-off:** (+) Bundle splitting più granulare, tree-shaking migliorato, meno boilerplate. (-) Curva di apprendimento per sviluppatori abituati a NgModules; alcuni plugin di terze parti ancora basati su NgModule richiedono wrapper.

---

### ADR-002 — Spring Batch 5 per la Pipeline ETL

**Contesto:** Il sistema deve gestire import di file XLSX/CSV da 10K a 500K righe in modo affidabile, con possibilità di riavvio in caso di errore.

**Alternative:**
1. Import sincrono in-memory nel controller REST.
2. Spring Batch 5 con job persistiti su DB.
3. Apache Kafka + consumer dedicato.

**Decisione:** Opzione 2 — Spring Batch 5.

**Trade-off:** (+) Checkpoint automatico, riavvio granulare, integrazione nativa con Spring; step disaccoppiati e testabili. (-) Overhead di configurazione iniziale; richiede schema `spring_batch` sul DB.

---

### ADR-003 — Cifratura Colonne PII con pgcrypto (vs. Applicativa)

**Contesto:** I dati PII (full_name, email) devono essere protetti a riposo. La cifratura può avvenire a livello applicativo (Java) o a livello DB (pgcrypto).

**Alternative:**
1. Cifratura applicativa con AES-256 in Java, chiavi in Vault.
2. pgcrypto con `pgp_sym_encrypt` e passphrase da Vault.
3. Transparent Data Encryption (TDE) a livello di filesystem.

**Decisione:** Opzione 2 — pgcrypto.

**Trade-off:** (+) I dati sono illeggibili persino per accessi diretti al DB senza la passphrase; query di decifrazione centralizzate in DB function. (-) Impossibilità di indicizzare campi cifrati (necessario hash separato per lookup); overhead CPU sul DB.

---

### ADR-004 — SSE per Progresso Import (vs. WebSocket)

**Contesto:** Il frontend deve mostrare il progresso in tempo reale durante l'import. Le opzioni sono SSE (unidirezionale) o WebSocket (bidirezionale).

**Alternative:**
1. Polling REST ogni 2 secondi.
2. Server-Sent Events (SSE).
3. WebSocket con STOMP.

**Decisione:** Opzione 2 — SSE.

**Trade-off:** (+) Semplicità implementativa, nativa nei browser moderni, supporto firewall/proxy migliore rispetto a WebSocket, sufficiente per comunicazione unidirezionale. (-) Non adatto a scenari bidirezionali futuri; richiede gestione dei `SseEmitter` lato server con attenzione al thread pool.

---

### ADR-005 — Schema DB Separato per Audit (rise_audit)

**Contesto:** I requisiti di compliance richiedono un audit trail immutabile. La questione è se usare una tabella audit nello stesso schema business o uno schema separato.

**Alternative:**
1. Tabella `audit_log` in `rise_core`.
2. Schema dedicato `rise_audit` con ruolo DB append-only.
3. Soluzione esterna (es. Elasticsearch per audit).

**Decisione:** Opzione 2 — schema `rise_audit` con ruolo PostgreSQL separato.

**Trade-off:** (+) Separazione netta dei privilegi; il ruolo applicativo `rise_app` non ha UPDATE/DELETE su `rise_audit`; il ruolo `rise_audit_writer` ha solo INSERT. (-) Più complessa la gestione dei ruoli DB; le query di join audit-business richiedono permessi espliciti.

---

## §9 Pipeline CI/CD

```
+──────────────────────────────────────────────────────────────────────────────+
|  CI/CD Pipeline — RISE Spending Effectiveness                               |
|  (GitHub Actions / GitLab CI)                                               |
|                                                                              |
|  +──────────────+                                                            |
|  |  git push /  |                                                            |
|  |  Pull Request|                                                            |
|  +──────+───────+                                                            |
|         |                                                                    |
|         v                                                                    |
|  +──────────────────────────────────────────────────────────────────────+   |
|  |  STAGE 1: LINT & BUILD                                               |   |
|  |  +─────────────────────+     +──────────────────────────────────+   |   |
|  |  | Frontend             |     | Backend                          |   |   |
|  |  | - npm ci             |     | - mvn verify -DskipTests         |   |   |
|  |  | - ng lint            |     | - Checkstyle + SpotBugs          |   |   |
|  |  | - ng build --prod    |     | - OWASP Dependency Check         |   |   |
|  |  +─────────────────────+     +──────────────────────────────────+   |   |
|  +──────────────────────────────────────────────────────────────────────+   |
|         |                                                                    |
|         v                                                                    |
|  +──────────────────────────────────────────────────────────────────────+   |
|  |  STAGE 2: TEST                                                       |   |
|  |  +─────────────────────+     +──────────────────────────────────+   |   |
|  |  | Frontend             |     | Backend                          |   |   |
|  |  | - Jest unit tests    |     | - JUnit 5 unit tests             |   |   |
|  |  | - Coverage > 80%     |     | - Testcontainers integration     |   |   |
|  |  +─────────────────────+     | - Coverage > 80% (JaCoCo)        |   |   |
|  |                              +──────────────────────────────────+   |   |
|  +──────────────────────────────────────────────────────────────────────+   |
|         |                                                                    |
|         v                                                                    |
|  +──────────────────────────────────────────────────────────────────────+   |
|  |  STAGE 3: SECURITY SCAN                                              |   |
|  |  - Trivy (image vulnerability scan)                                  |   |
|  |  - Semgrep / SonarQube SAST                                          |   |
|  |  - Gitleaks (secret detection)                                       |   |
|  +──────────────────────────────────────────────────────────────────────+   |
|         |                                                                    |
|         v                                                                    |
|  +──────────────────────────────────────────────────────────────────────+   |
|  |  STAGE 4: DOCKER BUILD & PUSH                                        |   |
|  |  - docker build frontend  -> registry/rise-frontend:sha             |   |
|  |  - docker build backend   -> registry/rise-backend:sha              |   |
|  |  - Push su Container Registry (GitHub Packages / ACR)               |   |
|  +──────────────────────────────────────────────────────────────────────+   |
|         |                                                                    |
|         v                                                                    |
|  +──────────────────────────────────────────────────────────────────────+   |
|  |  STAGE 5: DEPLOY STAGING                                             |   |
|  |  - helm upgrade --install rise-staging ./helm/rise                   |   |
|  |      --set image.tag=$SHA --namespace staging                        |   |
|  |  - Flyway migrate (staging DB)                                       |   |
|  |  - Smoke test (Newman / k6 baseline)                                 |   |
|  +──────────────────────────────────────────────────────────────────────+   |
|         |                                                                    |
|         |  (solo branch main/release dopo approvazione manuale)             |
|         v                                                                    |
|  +──────────────────────────────────────────────────────────────────────+   |
|  |  STAGE 6: DEPLOY PRODUCTION                                          |   |
|  |  - helm upgrade --install rise-prod ./helm/rise                      |   |
|  |      --set image.tag=$SHA --namespace production                     |   |
|  |  - Flyway migrate (production DB — con backup preventivo)            |   |
|  |  - Rolling update (maxUnavailable: 0, maxSurge: 1)                   |   |
|  |  - Post-deploy health check + rollback automatico se KO              |   |
|  +──────────────────────────────────────────────────────────────────────+   |
+──────────────────────────────────────────────────────────────────────────────+
```

---

## §10 Definition of Done

- [ ] **DOD-01** Il codice è revisionato (code review) da almeno un membro del team diverso dall'autore.
- [ ] **DOD-02** Tutti i test unitari passano (coverage >= 80% su nuovi moduli).
- [ ] **DOD-03** I test di integrazione con Testcontainers passano sull'ambiente CI.
- [ ] **DOD-04** Nessuna vulnerabilità CRITICAL/HIGH aperta nella scansione Trivy dell'immagine Docker.
- [ ] **DOD-05** Nessun secret in chiaro rilevato da Gitleaks nel diff del PR.
- [ ] **DOD-06** La pipeline CI/CD è verde (lint, build, test, security scan) senza warning soppressi.
- [ ] **DOD-07** Le migrazioni Flyway sono presenti, nominate correttamente (`V<n>__descrizione.sql`) e reversibili ove possibile.
- [ ] **DOD-08** Le API sono documentate in OpenAPI 3.1 (Springdoc) e lo schema è aggiornato.
- [ ] **DOD-09** Il country scoping è verificato con test dedicati (nessuna perdita di dato cross-country).
- [ ] **DOD-10** I campi PII sono cifrati correttamente nel DB e il masking è attivo negli export per Country Manager.
- [ ] **DOD-11** Gli audit log sono scritti correttamente per ogni operazione sensibile (import, modifica master data, export).
- [ ] **DOD-12** Il flusso OIDC/PKCE è testato su ambiente staging con l'IdP reale.
- [ ] **DOD-13** Il job Spring Batch è riavviabile in caso di errore parziale (test di restart verificato).
- [ ] **DOD-14** La SSE di progresso import funziona end-to-end su staging.
- [ ] **DOD-15** I NFR di performance sono verificati: P95 < 300 ms su endpoint critici (test k6).
- [ ] **DOD-16** Il throughput di import è >= 5.000 record/minuto su file di test benchmark (10K righe).
- [ ] **DOD-17** La Materialized View mv_kpi_monthly si aggiorna entro 5 minuti da un import completato.
- [ ] **DOD-18** La documentazione Swagger UI è accessibile su `/api/v1/swagger-ui.html` in staging.
- [ ] **DOD-19** I log applicativi sono in formato JSON strutturato e indicizzati su stack ELK/Grafana Loki.
- [ ] **DOD-20** Le metriche Micrometer sono visibili su Grafana con dashboard aggiornato.
- [ ] **DOD-21** Il rollout su production utilizza rolling update senza downtime (verificato con probe di disponibilità).
- [ ] **DOD-22** I backup del DB PostgreSQL sono verificati sul punto di ripristino (RPO < 15 min).
- [ ] **DOD-23** Il runbook operativo per il nuovo componente/feature è scritto e disponibile su Confluence.
- [ ] **DOD-24** La feature è stata dimostrata al Product Owner e accettata in sessione di demo.
- [ ] **DOD-25** I ticket Jira correlati sono chiusi e collegati al PR/tag di rilascio.
- [ ] **DOD-26** Nessuna regression su fixture di test E2E Cypress per i flussi critici (import, export, login).
- [ ] **DOD-27** Il CHANGELOG (`CHANGELOG.md`) è aggiornato con la voce relativa alla release.

---

## ARCH_AGENT_SIGNATURE_V2
This section must be present and must not be removed.
````