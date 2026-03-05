# RISE Spending Effectiveness – Architecture & Technical Design
**Versione:** 2.0 | **Data:** 2026-03-04 | **Autore:** Architect Agent

---

## §1 Contesto e Vincoli

Il progetto RISE Spending Effectiveness è una piattaforma B2B enterprise progettata per consolidare, validare e analizzare dati di retribuzione variabile e vendite a livello globale. Di seguito le assunzioni architetturali fondamentali.

| ID    | Assunzione Architetturale                                                                                                  |
|-------|----------------------------------------------------------------------------------------------------------------------------|
| A-01  | Il sistema è multi-tenant logico: i dati sono isolati per country tramite country_code; non esiste isolamento fisico di schema per tenant. |
| A-02  | L'autenticazione è delegata a un Identity Provider esterno (es. Keycloak/Azure AD) via OIDC/PKCE; il backend emette e valida JWT stateless. |
| A-03  | Il database primario è PostgreSQL 15+ su infrastruttura managed (es. AWS RDS o Azure Database for PostgreSQL). Nessun ORM alternativo. |
| A-04  | Il frontend Angular 17 è una Single Page Application (SPA) distribuita su CDN; comunica col backend esclusivamente via REST + SSE. |
| A-05  | Lo schema `rise_core` contiene tutti i dati di business; lo schema `rise_audit` è immutabile e append-only per compliance GDPR/SOX. |
| A-06  | Spring Batch esegue ogni pipeline di import in job sincrono lato server; il client monitora il progresso tramite SSE senza polling attivo. |
| A-07  | La chiave di deduplicazione `(employee_id, shop_id, record_month, record_year)` è universale per tutti i tipi di record. |
| A-08  | I dati personali (full_name, email) sono cifrati at-rest con pgcrypto (AES-256); la chiave KEK è gestita da AWS KMS / Azure Key Vault. |
| A-09  | La materialized view `mv_kpi_monthly` viene aggiornata con `REFRESH CONCURRENTLY` al termine di ogni import job riuscito. |
| A-10  | Il deployment target è Kubernetes (EKS/AKS); ogni microcomponente ha HPA configurato; il backend scala orizzontalmente senza stato locale. |

---

## §2 Diagrammi C4 (ASCII)

### §2.1 System Context

```
╔══════════════════════════════════════════════════════════════════════════════╗
║                         RISE Spending Effectiveness                          ║
║                           [System Context]                                   ║
╚══════════════════════════════════════════════════════════════════════════════╝

          ┌─────────────────┐          ┌─────────────────┐
          │  Global Admin   │          │ Country Manager │
          │   [Person]      │          │   [Person]      │
          └────────┬────────┘          └────────┬────────┘
                   │                            │
                   │  HTTPS / Browser           │  HTTPS / Browser
                   ▼                            ▼
         ┌──────────────────────────────────────────────┐
         │                                              │
         │         RISE Spending Effectiveness          │
         │              [Software System]               │
         │   Piattaforma B2B per consolidamento         │
         │   retribuzioni variabili e dati vendite      │
         │                                              │
         └──────────┬───────────────────┬──────────────┘
                    │                   │
          ┌─────────▼──────┐  ┌─────────▼──────────────┐
          │  Identity      │  │   Data Warehouse /      │
          │  Provider      │  │   External HR System    │
          │  [Keycloak /   │  │   [External System]     │
          │   Azure AD]    │  │   CSV / Excel Import    │
          │  OIDC/PKCE     │  └────────────────────────-┘
          └────────────────┘
                    │
          ┌─────────▼──────┐
          │  System Admin  │
          │   [Person]     │
          └────────────────┘
```

### §2.2 Container Diagram

```
╔══════════════════════════════════════════════════════════════════════════════════╗
║                    RISE Spending Effectiveness – Container Diagram               ║
╚══════════════════════════════════════════════════════════════════════════════════╝

 Browser
 ┌─────────────────────────────────────────────────────────────────────────┐
 │                    Angular 17 SPA  [TypeScript]                         │
 │  - Modules: Auth, Dashboard, Import-Wizard, Master-Data, Admin, Export  │
 │  - NgRx Store, Angular Material, Chart.js, SSE Client                   │
 └──────────────────────────────┬──────────────────────────────────────────┘
                                │  REST/HTTPS :443
                                │  SSE /api/v1/import/progress/{jobId}
                                ▼
 ┌─────────────────────────────────────────────────────────────────────────┐
 │              Spring Boot 3.x API  [Java 21 / Kotlin]                    │
 │  - Controllers: Auth, MasterData, Import, KPI, Export, Admin            │
 │  - Spring Security (JWT), Spring Batch, Spring Data JPA                 │
 │  - Services: ImportOrchestrator, KpiService, ExportService, AuditService│
 └──────┬───────────────────────────┬────────────────────────┬─────────────┘
        │  JDBC/SSL                 │  pgcrypto              │  S3/Blob
        ▼                           ▼                        ▼
 ┌─────────────┐          ┌─────────────────┐     ┌──────────────────────┐
 │ PostgreSQL  │          │  rise_audit DB  │     │  Object Storage      │
 │   15+       │          │  (append-only)  │     │  Export Files / CSV  │
 │  rise_core  │          │  audit_events   │     │  Templates           │
 │  schema     │          │  audit_imports  │     └──────────────────────┘
 └─────────────┘          └─────────────────┘
        │
 ┌──────▼──────────────────────────────────────────────────────────────────┐
 │           Redis (Cache Layer)  [Sessioni / KPI Cache / Dedup Lock]      │
 └─────────────────────────────────────────────────────────────────────────┘
        │
 ┌──────▼──────────────────────────────────────────────────────────────────┐
 │           Identity Provider  [Keycloak / Azure AD]  OIDC/PKCE           │
 └─────────────────────────────────────────────────────────────────────────┘
```

### §2.3 Component Diagram – Spring Boot

```
╔══════════════════════════════════════════════════════════════════════════════╗
║              Spring Boot 3.x – Component Diagram (Internals)                 ║
╚══════════════════════════════════════════════════════════════════════════════╝

 ┌─────────────────────────────────────────────────────────────────────────┐
 │                         API Layer (Controllers)                          │
 │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌───────────────┐  │
 │  │ AuthController│ │MasterDataCtrl│ │ImportController│ │KpiController │  │
 │  └──────┬───────┘ └──────┬───────┘ └──────┬────────┘ └──────┬───────┘  │
 │  ┌───────────────┐ ┌──────────────────────────────────────────────────┐ │
 │  │ExportController│ │          AdminController                        │ │
 │  └───────────────┘ └──────────────────────────────────────────────────┘ │
 └────────────────────────────────┬────────────────────────────────────────┘
                                  │
 ┌────────────────────────────────▼────────────────────────────────────────┐
 │                         Service Layer                                    │
 │  ┌─────────────────┐  ┌──────────────────┐  ┌────────────────────────┐  │
 │  │ImportOrchestrator│  │   KpiService     │  │    ExportService       │  │
 │  │  (Spring Batch) │  │ (mat.view query) │  │  (CSV/Excel gen.)      │  │
 │  └────────┬────────┘  └──────────────────┘  └────────────────────────┘  │
 │  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────────────┐  │
 │  │  AuditService    │  │  CryptoService   │  │  CountryScopeService   │  │
 │  │  (audit schema)  │  │  (pgcrypto AES)  │  │  (JPA Specification)   │  │
 │  └──────────────────┘  └──────────────────┘  └────────────────────────┘  │
 └────────────────────────────────┬────────────────────────────────────────┘
                                  │
 ┌────────────────────────────────▼────────────────────────────────────────┐
 │                     Spring Batch Pipeline                                │
 │  FileItemReader → MappingTransformProcessor → ValidationProcessor        │
 │       → DuplicateDetectionProcessor → RecordWriter → ImportLogClosing    │
 └────────────────────────────────┬────────────────────────────────────────┘
                                  │
 ┌────────────────────────────────▼────────────────────────────────────────┐
 │               Persistence Layer  (Spring Data JPA + Repositories)        │
 │  CompensationRecordRepo | SalesRecordRepo | ImportJobRepo | UserRepo     │
 │  ShopRepo | CountryRepo | TemplateDefinitionRepo | AuditEventRepo        │
 └────────────────────────────────┬────────────────────────────────────────┘
                                  │  JDBC
                                  ▼
                          PostgreSQL 15+
```

---

## §3 Frontend Angular

### §3.1 Tech Stack

| Tecnologia              | Versione   | Utilizzo                                                    |
|-------------------------|------------|-------------------------------------------------------------|
| Angular                 | 17.x       | Framework SPA principale, standalone components             |
| TypeScript              | 5.3.x      | Linguaggio principale, strict mode abilitato                |
| NgRx                    | 17.x       | State management globale (Store, Effects, Selectors)        |
| Angular Material        | 17.x       | UI Component Library, theming Material Design 3             |
| Chart.js + ng2-charts   | 4.x / 5.x  | Grafici KPI Dashboard (bar, line, doughnut)                 |
| RxJS                    | 7.8.x      | Programmazione reattiva, gestione stream SSE                 |
| Angular Router          | 17.x       | Routing lazy-loaded per moduli feature                      |
| Angular Forms           | 17.x       | Reactive Forms per Import Wizard e Master Data              |
| @angular/common/http    | 17.x       | HttpClient con interceptor JWT e error handling             |
| Nx Monorepo             | 17.x       | Workspace management, build caching, affected detection     |
| Jest                    | 29.x       | Unit testing con Angular Testing Library                    |
| Cypress                 | 13.x       | E2E testing, component testing automatizzato                |
| Papa Parse              | 5.x        | Parsing CSV lato client per preview pre-import              |

### §3.2 Struttura Directory src/app/

```
src/app/
├── core/
│   ├── auth/
│   │   ├── auth.service.ts
│   │   ├── auth.guard.ts
│   │   ├── oidc-callback.component.ts
│   │   └── token-interceptor.ts
│   ├── http/
│   │   ├── api.interceptor.ts
│   │   └── error.interceptor.ts
│   ├── models/
│   │   ├── user.model.ts
│   │   ├── country.model.ts
│   │   ├── compensation-record.model.ts
│   │   ├── sales-record.model.ts
│   │   └── import-job.model.ts
│   └── services/
│       ├── notification.service.ts
│       └── sse.service.ts
│
├── shared/
│   ├── components/
│   │   ├── data-table/
│   │   ├── confirm-dialog/
│   │   ├── loading-spinner/
│   │   ├── error-banner/
│   │   └── country-selector/
│   ├── directives/
│   │   ├── role.directive.ts
│   │   └── country-scope.directive.ts
│   └── pipes/
│       ├── currency-format.pipe.ts
│       └── masked-email.pipe.ts
│
├── features/
│   ├── dashboard/
│   │   ├── dashboard.module.ts
│   │   ├── dashboard.component.ts
│   │   ├── kpi-cards/
│   │   │   └── kpi-cards.component.ts
│   │   ├── sales-chart/
│   │   │   └── sales-chart.component.ts
│   │   └── compensation-chart/
│   │       └── compensation-chart.component.ts
│   │
│   ├── import-wizard/
│   │   ├── import-wizard.module.ts
│   │   ├── import-wizard.component.ts
│   │   ├── store/
│   │   │   ├── import-wizard.actions.ts
│   │   │   ├── import-wizard.reducer.ts
│   │   │   ├── import-wizard.effects.ts
│   │   │   └── import-wizard.selectors.ts
│   │   ├── step1-template-definition/
│   │   │   └── template-definition.component.ts
│   │   ├── step2-source-registration/
│   │   │   └── source-registration.component.ts
│   │   ├── step3-manual-import/
│   │   │   ├── manual-import.component.ts
│   │   │   └── progress-bar/
│   │   │       └── import-progress.component.ts
│   │   └── dedup-strategy-selector/
│   │       └── dedup-strategy.component.ts
│   │
│   ├── master-data/
│   │   ├── master-data.module.ts
│   │   ├── employees/
│   │   │   └── employees.component.ts
│   │   ├── shops/
│   │   │   └── shops.component.ts
│   │   └── countries/
│   │       └── countries.component.ts
│   │
│   ├── export/
│   │   ├── export.module.ts
│   │   ├── export.component.ts
│   │   └── export-filter/
│   │       └── export-filter.component.ts
│   │
│   └── admin/
│       ├── admin.module.ts
│       ├── user-management/
│       │   └── user-management.component.ts
│       ├── role-assignment/
│       │   └── role-assignment.component.ts
│       └── audit-log/
│           └── audit-log.component.ts
│
├── app.routes.ts
├── app.config.ts
└── app.component.ts
```

### §3.3 State Machine Import Wizard

```
╔══════════════════════════════════════════════════════════════════════════════╗
║                   Import Wizard – State Machine                              ║
╚══════════════════════════════════════════════════════════════════════════════╝

                            ┌─────────────────┐
                            │      IDLE        │
                            │  (wizard chiuso) │
                            └────────┬─────────┘
                                     │ [Utente apre wizard]
                                     ▼
                     ┌───────────────────────────────┐
                     │  STEP_1_TEMPLATE_DEFINITION    │
                     │  - Selezione tipo record       │
                     │  - Mapping colonne CSV/Excel   │
                     │  - Validazione header template │
                     └───────────┬───────────-────────┘
                                 │ [Salva Template]      ◄─── [Modifica Template]
                  ┌──────────────▼──────────────────┐
                  │  STEP_2_SOURCE_REGISTRATION      │
                  │  - Nome sorgente dati            │
                  │  - Upload file CSV/XLSX          │
                  │  - Preview dati (prime 10 righe) │
                  │  - Selezione strategia Dedup     │
                  │    (SKIP | OVERWRITE)            │
                  └──────────────┬──────────────────┘
                                 │ [Conferma Sorgente]   ◄─── [Torna a Step 1]
                  ┌──────────────▼──────────────────┐
                  │  STEP_3_MANUAL_IMPORT_EXECUTION  │
                  │  - Riepilogo configurazione      │
                  │  - Avvio pipeline Spring Batch   │
                  │  - Barra progresso SSE real-time │
                  └──────────────┬──────────────────┘
                                 │
                    ┌────────────┴────────────┐
                    ▼                         ▼
         ┌─────────────────┐       ┌──────────────────┐
         │   IMPORT_SUCCESS │       │   IMPORT_FAILED  │
         │  - Log record    │       │  - Errori detail │
         │  - KPI refresh   │       │  - Retry button  │
         │  - Notifica SSE  │       │  - Download log  │
         └────────┬─────────┘       └────────┬─────────┘
                  │                           │
                  └─────────────┬─────────────┘
                                ▼
                        ┌───────────────┐
                        │  WIZARD_DONE  │
                        │  (reset/close)│
                        └───────────────┘

  Transizioni di errore (da qualsiasi stato → ERROR_STATE):
  - Timeout connessione SSE      → Retry automatico (max 3)
  - Errore 5xx backend           → Error banner + log tecnico
  - Validation error Step 1/2    → Inline form error (no cambio stato)
```

---

## §4 Backend Spring Boot

### §4.1 Tech Stack

| Tecnologia                    | Versione   | Utilizzo                                                         |
|-------------------------------|------------|------------------------------------------------------------------|
| Java                          | 21 LTS     | Linguaggio principale, virtual threads (Loom)                    |
| Spring Boot                   | 3.2.x      | Framework principale, auto-configuration, Actuator               |
| Spring Security               | 6.2.x      | JWT validation, RBAC, OIDC resource server                       |
| Spring Batch                  | 5.1.x      | Pipeline di import chunked (chunk size 500)                      |
| Spring Data JPA               | 3.2.x      | ORM con Hibernate 6.x, JPA Specification per country scoping     |
| Spring Data Redis             | 3.2.x      | Cache KPI, distributed lock per dedup, session cache             |
| PostgreSQL JDBC Driver        | 42.7.x     | Connettività DB, supporto pgcrypto nativo                        |
| Flyway                        | 10.x       | Database migrations versionate, baseline su prod                 |
| Hibernate Validator           | 8.x        | Validazione bean (JSR-380), custom constraints                   |
| Apache POI                    | 5.3.x      | Lettura file XLSX nella pipeline import                          |
| OpenCSV                       | 5.9.x      | Parsing CSV ad alte prestazioni nel FileItemReader               |
| SpringDoc OpenAPI             | 2.3.x      | Generazione automatica spec OpenAPI 3.1 + Swagger UI            |
| Micrometer + Prometheus       | 1.12.x     | Metriche applicative, integrazione Grafana                       |

### §4.2 Pipeline Spring Batch

```
╔══════════════════════════════════════════════════════════════════════════════════╗
║                 Spring Batch Import Pipeline – Dettaglio Completo               ║
╚══════════════════════════════════════════════════════════════════════════════════╝

  JOB: ImportJob  (chunk-size: 500, restart: RESTART_FROM_FAILED_CHUNK)

  ┌─────────────────────────────────────────────────────────────────────────────┐
  │  STEP 1: FileItemReader                                                      │
  │  ─────────────────────────────────────────────────────────────────────────  │
  │  • Legge CSV/XLSX da staging area (Object Storage)                           │
  │  • Usa OpenCSV FlatFileItemReader o POI-based reader per XLSX                │
  │  • Emette RawImportRow (Map<String,String>) per ogni riga                    │
  │  • Gestisce BOM UTF-8, encoding, skip header                                 │
  │  • Skip policy: salta righe vuote, logga SkippedItemException                │
  └──────────────────────────────────┬──────────────────────────────────────────┘
                                     │ chunk di 500 RawImportRow
                                     ▼
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │  STEP 2: MappingTransformProcessor                                           │
  │  ─────────────────────────────────────────────────────────────────────────  │
  │  • Legge TemplateDefinition salvata in Step 1 del wizard                     │
  │  • Mappa colonne CSV → campi dominio (es. "Commissioni" → commission)        │
  │  • Normalizza tipi: stringhe → BigDecimal, date → LocalDate                 │
  │  • Supporta campi Compensazione: FTE, Commission, Quarterly Bonus,           │
  │    Annual Bonus, Extra Booster, Other Compensation Type                      │
  │  • Supporta campi Vendite: Total Sales, HA Sales, Monthly Target,            │
  │    Quarterly Target, Annual Target, Other Sales Type                         │
  │  • Output: DomainRecord (CompensationRecord | SalesRecord)                  │
  └──────────────────────────────────┬──────────────────────────────────────────┘
                                     │
                                     ▼
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │  STEP 3: ValidationProcessor                                                 │
  │  ─────────────────────────────────────────────────────────────────────────  │
  │  • Hibernate Validator: @NotNull, @Min, @Max, @Pattern su tutti i campi     │
  │  • Validazione business: employee_id esiste in rise_core.employees           │
  │  • Validazione business: shop_id esiste in rise_core.shops                  │
  │  • record_month in [1..12], record_year in [2000..2099]                     │
  │  • Valori monetari ≥ 0 (BigDecimal)                                         │
  │  • Skip threshold: se >5% righe invalide → job FAILED                       │
  │  • Record invalidi loggati in import_validation_errors con row_number        │
  └──────────────────────────────────┬──────────────────────────────────────────┘
                                     │
                                     ▼
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │  STEP 4: DuplicateDetectionProcessor                                         │
  │  ─────────────────────────────────────────────────────────────────────────  │
  │  • Chiave dedup: (employee_id, shop_id, record_month, record_year)           │
  │  • Lookup in Redis (cache 1h) + fallback DB                                 │
  │  • Strategia SKIP: scarta il record duplicato, logga SKIPPED                 │
  │  • Strategia OVERWRITE: marca il record esistente come superseded,           │
  │    inserisce nuova versione con version_number incrementato                  │
  │  • Distributed lock via Redisson per evitare race conditions                 │
  │  • Statistiche: processed/skipped/overwritten aggiornate in ImportJobContext │
  └──────────────────────────────────┬──────────────────────────────────────────┘
                                     │
                                     ▼
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │  STEP 5: RecordWriter                                                        │
  │  ─────────────────────────────────────────────────────────────────────────  │
  │  • JpaItemWriter batch insert con flush ogni 500 record                      │
  │  • Cifra full_name/email con pgcrypto prima della persistenza                │
  │  • Inserimento parallelo in rise_audit.audit_imports (immutabile)            │
  │  • SSE event emesso ogni chunk: {"processed": N, "total": M, "pct": P}      │
  │  • Retry policy: 3 tentativi su SQLException transiente (deadlock,timeout)  │
  └──────────────────────────────────┬──────────────────────────────────────────┘
                                     │
                                     ▼
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │  STEP 6: ImportLogClosingTasklet                                             │
  │  ─────────────────────────────────────────────────────────────────────────  │
  │  • Aggiorna import_jobs: status=COMPLETED/FAILED, ended_at=NOW()            │
  │  • Esegue REFRESH MATERIALIZED VIEW CONCURRENTLY mv_kpi_monthly             │
  │  • Emette SSE finale: {"status":"COMPLETED","importJobId":"..."}             │
  │  • Pulisce file staging da Object Storage                                    │
  │  • Scrive evento in rise_audit.audit_events (tipo: IMPORT_COMPLETED)        │
  └─────────────────────────────────────────────────────────────────────────────┘

  Monitoraggio: JobExplorer Spring Batch + tabelle BATCH_* nel rise_core schema
  Restart: RESTART_FROM_FAILED_CHUNK — riprende dall'ultimo chunk riuscito
```

---

## §5 Contratti API

### §5.1 URL Base e Versionamento

```
Base URL:   https://api.rise.example.com/api/v1
Auth:       Bearer <JWT>  (header Authorization)
Content-Type: application/json
API Version: v1 (path-based versioning)
Deprecation: header Deprecation + Sunset per versioni obsolete
```

### §5.2 Endpoint Master Data

| Metodo | Path                              | Descrizione                                      | Ruolo Minimo    |
|--------|-----------------------------------|--------------------------------------------------|-----------------|
| GET    | /master-data/employees            | Lista paginata employee (country-scoped)         | COUNTRY_MANAGER |
| POST   | /master-data/employees            | Crea nuovo employee                              | COUNTRY_MANAGER |
| GET    | /master-data/employees/{id}       | Dettaglio singolo employee                       | COUNTRY_MANAGER |
| PUT    | /master-data/employees/{id}       | Aggiorna employee (full update)                  | COUNTRY_MANAGER |
| PATCH  | /master-data/employees/{id}       | Aggiornamento parziale employee                  | COUNTRY_MANAGER |
| DELETE | /master-data/employees/{id}       | Soft-delete employee (is_active=false)           | GLOBAL_ADMIN    |
| GET    | /master-data/shops                | Lista paginata shop (country-scoped)             | COUNTRY_MANAGER |
| POST   | /master-data/shops                | Crea nuovo shop                                  | COUNTRY_MANAGER |
| GET    | /master-data/shops/{id}           | Dettaglio singolo shop                           | COUNTRY_MANAGER |
| PUT    | /master-data/shops/{id}           | Aggiorna shop                                    | COUNTRY_MANAGER |
| GET    | /master-data/countries            | Lista di tutti i country (non scoped)            | SYSTEM_ADMIN    |
| POST   | /master-data/countries            | Crea nuovo country                               | GLOBAL_ADMIN    |
| GET    | /master-data/countries/{code}     | Dettaglio country per ISO code                   | SYSTEM_ADMIN    |
| PUT    | /master-data/countries/{code}     | Aggiorna country                                 | GLOBAL_ADMIN    |

### §5.3 Endpoint Import

| Metodo | Path                                       | Descrizione                                     | Ruolo Minimo    |
|--------|--------------------------------------------|-------------------------------------------------|-----------------|
| GET    | /import/templates                          | Lista template definiti                         | COUNTRY_MANAGER |
| POST   | /import/templates                          | Crea nuovo template definition (Step 1)         | COUNTRY_MANAGER |
| GET    | /import/templates/{id}                     | Dettaglio template                              | COUNTRY_MANAGER |
| PUT    | /import/templates/{id}                     | Aggiorna template                               | COUNTRY_MANAGER |
| DELETE | /import/templates/{id}                     | Elimina template                                | GLOBAL_ADMIN    |
| POST   | /import/sources                            | Registra sorgente dati (Step 2) + upload file   | COUNTRY_MANAGER |
| GET    | /import/sources/{id}/preview               | Preview prime 10 righe file caricato            | COUNTRY_MANAGER |
| POST   | /import/execute                            | Avvia pipeline Spring Batch (Step 3)            | COUNTRY_MANAGER |
| GET    | /import/status/{jobId}                     | Stato corrente import job (polling)             | COUNTRY_MANAGER |
| GET    | /import/progress/{jobId}                   | SSE stream progresso real-time                  | COUNTRY_MANAGER |
| GET    | /import/history                            | Storico import job (country-scoped, paginato)   | COUNTRY_MANAGER |
| GET    | /import/history/{jobId}/errors             | Dettaglio errori di un import job               | COUNTRY_MANAGER |

**Esempio: POST /api/v1/import/execute — Request**
```json
{
  "sourceId": "src-7f3a9b12",
  "templateId": "tmpl-002c4d8e",
  "dedupStrategy": "OVERWRITE",
  "countryCode": "IT",
  "recordType": "COMPENSATION",
  "notes": "Import mensile marzo 2026 – Italia"
}
```

**Esempio: POST /api/v1/import/execute — Response (202 Accepted)**
```json
{
  "importJobId": "job-550e8400-e29b-41d4-a716-446655440000",
  "status": "STARTED",
  "countryCode": "IT",
  "recordType": "COMPENSATION",
  "dedupStrategy": "OVERWRITE",
  "startedAt": "2026-03-04T10:15:30Z",
  "estimatedRows": 1250,
  "sseUrl": "/api/v1/import/progress/job-550e8400-e29b-41d4-a716-446655440000"
}
```

**Esempio: GET /api/v1/import/status/{jobId} — Response (200 OK)**
```json
{
  "importJobId": "job-550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "countryCode": "IT",
  "startedAt": "2026-03-04T10:15:30Z",
  "endedAt": "2026-03-04T10:18:45Z",
  "totalRows": 1250,
  "processedRows": 1250,
  "skippedRows": 12,
  "overwrittenRows": 38,
  "errorRows": 0,
  "durationSeconds": 195
}
```

### §5.4 Endpoint KPI

| Metodo | Path                          | Descrizione                                            | Ruolo Minimo    |
|--------|-------------------------------|--------------------------------------------------------|-----------------|
| GET    | /kpi/summary                  | KPI aggregati mensili (da mv_kpi_monthly)              | COUNTRY_MANAGER |
| GET    | /kpi/compensation             | Serie storica retribuzione variabile per country       | COUNTRY_MANAGER |
| GET    | /kpi/sales                    | Serie storica vendite per country/shop                 | COUNTRY_MANAGER |
| GET    | /kpi/targets                  | Confronto target vs actual (mensile/trimestrale/annuo) | COUNTRY_MANAGER |
| GET    | /kpi/top-performers           | Top N employee per commissioni o vendite               | GLOBAL_ADMIN    |
| GET    | /kpi/trend-analysis           | Analisi trend YoY / MoM per ogni KPI                  | GLOBAL_ADMIN    |

**Esempio: GET /api/v1/kpi/summary?countryCode=IT&year=2026&month=2 — Response (200 OK)**
```json
{
  "countryCode": "IT",
  "period": { "year": 2026, "month": 2 },
  "compensation": {
    "totalFte": 342,
    "totalCommission": 128450.75,
    "totalQuarterlyBonus": 45200.00,
    "totalAnnualBonus": 0.00,
    "totalExtraBooster": 8750.50,
    "totalOtherCompensation": 3200.00,
    "grandTotal": 185601.25
  },
  "sales": {
    "totalSales": 4825000.00,
    "totalHaSales": 620000.00,
    "monthlyTargetAchievementPct": 96.5,
    "quarterlyTargetAchievementPct": 94.2,
    "annualTargetAchievementPct": 31.8
  },
  "lastRefreshedAt": "2026-03-04T10:18:50Z"
}
```

### §5.5 Endpoint Export

| Metodo | Path                          | Descrizione                                            | Ruolo Minimo    |
|--------|-------------------------------|--------------------------------------------------------|-----------------|
| POST   | /export/compensation          | Export CSV/XLSX dati compensazione (filtri applicati)  | COUNTRY_MANAGER |
| POST   | /export/sales                 | Export CSV/XLSX dati vendite (filtri applicati)        | COUNTRY_MANAGER |
| POST   | /export/full-db               | Export completo DB (solo Global Admin, zip)            | GLOBAL_ADMIN    |
| GET    | /export/jobs/{exportJobId}    | Stato job export asincrono                             | COUNTRY_MANAGER |
| GET    | /export/download/{token}      | Download file export (token temporaneo 15 min)        | COUNTRY_MANAGER |
| GET    | /export/templates/csv         | Download template CSV vuoto per import                 | COUNTRY_MANAGER |
| GET    | /export/templates/xlsx        | Download template XLSX vuoto per import                | COUNTRY_MANAGER |

### §5.6 Endpoint Admin

| Metodo | Path                              | Descrizione                                       | Ruolo Minimo  |
|--------|-----------------------------------|---------------------------------------------------|---------------|
| GET    | /admin/users                      | Lista paginata utenti sistema                     | SYSTEM_ADMIN  |
| POST   | /admin/users                      | Crea utente (provisioning manuale)                | SYSTEM_ADMIN  |
| GET    | /admin/users/{userId}             | Dettaglio utente                                  | SYSTEM_ADMIN  |
| PUT    | /admin/users/{userId}/roles       | Assegna/rimuovi ruoli utente                      | GLOBAL_ADMIN  |
| PUT    | /admin/users/{userId}/countries   | Assegna country scope a utente                    | GLOBAL_ADMIN  |
| DELETE | /admin/users/{userId}             | Disabilita utente (soft-delete)                   | GLOBAL_ADMIN  |
| GET    | /admin/audit-log                  | Log audit paginato (rise_audit schema)            | SYSTEM_ADMIN  |
| GET    | /admin/audit-log/{eventId}        | Dettaglio singolo evento audit                    | SYSTEM_ADMIN  |
| GET    | /admin/system/health              | Health check dettagliato (DB, Redis, Batch)       | SYSTEM_ADMIN  |
| GET    | /admin/system/metrics             | Metriche Micrometer (Prometheus format)           | SYSTEM_ADMIN  |

### §5.7 Modello Errore Standard

```json
{
  "timestamp": "2026-03-04T10:22:15.123Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "code": "RISE_VALIDATION_001",
  "message": "Il campo 'record_month' deve essere compreso tra 1 e 12. Valore ricevuto: 13",
  "path": "/api/v1/import/execute",
  "requestId": "req-a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "details": [
    {
      "field": "record_month",
      "rejectedValue": 13,
      "constraint": "Range",
      "message": "must be between 1 and 12"
    }
  ]
}
```

---

## §6 Sicurezza

### §6.1 Flow OIDC/PKCE

```
╔══════════════════════════════════════════════════════════════════════════════╗
║                  Flusso di Autenticazione OIDC / PKCE                        ║
╚══════════════════════════════════════════════════════════════════════════════╝

  Browser (Angular SPA)      Identity Provider (IdP)      Spring Boot API
         │                          │                            │
         │  1. Genera code_verifier │                            │
         │     e code_challenge     │                            │
         │  ──────────────────────► │                            │
         │                          │                            │
         │  2. GET /authorize       │                            │
         │     ?response_type=code  │                            │
         │     &client_id=rise-spa  │                            │
         │     &code_challenge=...  │                            │
         │     &scope=openid profile│                            │
         │  ──────────────────────► │                            │
         │                          │                            │
         │  3. Login Form (UI IdP)  │                            │
         │  ◄────────────────────── │                            │
         │                          │                            │
         │  4. Credenziali utente   │                            │
         │  ──────────────────────► │                            │
         │                          │                            │
         │  5. Authorization Code   │                            │
         │  ◄────────────────────── │                            │
         │                          │                            │
         │  6. POST /token          │                            │
         │     code + code_verifier │                            │
         │  ──────────────────────► │                            │
         │                          │                            │
         │  7. access_token (JWT)   │                            │
         │     + refresh_token      │                            │
         │     + id_token           │                            │
         │  ◄────────────────────── │                            │
         │                          │                            │
         │  8. GET /api/v1/kpi/...  │                            │
         │     Authorization: Bearer <access_token>             │
         │  ─────────────────────────────────────────────────► │
         │                          │                            │
         │                          │  9. Verifica JWT (JWKS)   │
         │                          │  ◄────────────────────────│
         │                          │                            │
         │                          │  10. JWKS public keys     │
         │                          │  ──────────────────────►  │
         │                          │                            │
         │  11. Response dati       │                            │
         │  ◄─────────────────────────────────────────────────  │
         │                          │                            │
         │  12. Token refresh (RT)  │                            │
         │      ogni 5 min prima    │                            │
         │      della scadenza AT   │                            │
         │  ──────────────────────► │                            │
```

### §6.2 Matrice RBAC

| Funzionalità                            | GLOBAL_ADMIN | COUNTRY_MANAGER | SYSTEM_ADMIN |
|-----------------------------------------|:------------:|:---------------:|:------------:|
| Visualizza dashboard KPI (own country)  | ✅           | ✅              | ❌           |
| Visualizza dashboard KPI (tutti)        | ✅           | ❌              | ❌           |
| Esegui Import Wizard                    | ✅           | ✅              | ❌           |
| Crea/Modifica Template Import           | ✅           | ✅              | ❌           |
| Elimina Template Import                 | ✅           | ❌              | ❌           |
| Gestione Master Data (own country)      | ✅           | ✅              | ❌           |
| Gestione Master Data (tutti)            | ✅           | ❌              | ❌           |
| Export dati propria country             | ✅           | ✅              | ❌           |
| Export completo DB                      | ✅           | ❌              | ❌           |
| Gestione Utenti                         | ❌           | ❌              | ✅           |
| Assegnazione Ruoli                      | ✅           | ❌              | ❌           |
| Assegnazione Country Scope              | ✅           | ❌              | ❌           |
| Visualizza Audit Log                    | ✅           | ❌              | ✅           |
| Crea/Modifica Country                   | ✅           | ❌              | ❌           |
| Accesso Metriche di Sistema             | ❌           | ❌              | ✅           |

### §6.3 Country Scoping

Il country scoping è implementato tramite **JPA Specification** (`org.springframework.data.jpa.domain.Specification<T>`) applicata automaticamente a tutte le query che accedono a dati business.

**Meccanismo:**
1. Il JWT contiene il claim `country_codes: ["IT","DE"]` (lista country assegnate all'utente).
2. Un `@Component CountryScopeContext` estrae tale claim tramite `SecurityContextHolder` e lo rende disponibile come thread-local via `RequestContextHolder`.
3. Ogni Repository che espone dati scoped implementa `JpaSpecificationExecutor<T>`.
4. Un `CountryScopeSpecification<T>` centralizzato genera automaticamente il predicato JPA:
   ```java
   (root, query, cb) -> root.get("countryCode").in(currentUserCountryCodes)
   ```
5. Il `CountryScopeAspect` (AOP `@Around`) intercetta tutti i metodi di service annotati con `@CountryScoped` e inietta la specifica nel repository call.
6. Il `GLOBAL_ADMIN` bypassa il filtro (lista `country_codes` non applicata).
7. Tentativi di accesso a country non assegnate restituiscono `403 Forbidden` con codice `RISE_AUTH_003`.

### §6.4 Controlli GDPR

| Controllo                         | Implementazione                                                                             |
|-----------------------------------|---------------------------------------------------------------------------------------------|
| Cifratura dati personali at-rest  | pgcrypto `pgp_sym_encrypt(full_name, :kek)` → BYTEA colonne `full_name_enc`, `email_enc`   |
| Gestione chiavi crittografiche    | KEK memorizzata in AWS KMS / Azure Key Vault; rotazione automatica annuale                  |
| Masking nei report/export         | `ExportService` maschera email (es. `j***@example.com`) salvo ruolo GLOBAL_ADMIN            |
| Audit trail immutabile            | Schema `rise_audit` append-only; no UPDATE/DELETE via role PostgreSQL `audit_writer`        |
| Right to Access                   | Endpoint `/admin/users/{id}/personal-data` restituisce dati decifrati (solo GLOBAL_ADMIN)  |
| Right to Erasure                  | Pseudoanonimizzazione: `full_name_enc` / `email_enc` → NULL + flag `is_anonymised=true`    |
| Retention Policy                  | Job schedulato mensile rimuove/anonimizza record con `record_year` < NOW() - 7 anni         |
| Data Transfer                     | TLS 1.3 obbligatorio su tutte le connessioni; HSTS header impostato                         |

---

## §7 NFR

| NFR                       | Target                                         | Implementazione                                                            |
|---------------------------|------------------------------------------------|----------------------------------------------------------------------------|
| Disponibilità             | 99.9% uptime mensile                          | K8s multi-zone, HPA, PodDisruptionBudget, health probes Liveness/Readiness |
| Latenza API               | p95 < 300ms per query non-batch               | Connection pool HikariCP (max 20), Redis cache KPI, indici compositi PG    |
| Throughput Import         | ≥ 10.000 record/minuto                        | Spring Batch chunk 500, virtual threads Java 21, batch insert JDBC         |
| Scalabilità               | Scale-out orizzontale a 0 stato               | Kubernetes HPA (CPU 70%), Redis per stato distribuito, JWT stateless        |
| Durabilità Dati           | RPO ≤ 1h, RTO ≤ 4h                           | PostgreSQL RDS Multi-AZ, PITR abilitato, backup giornaliero su S3          |
| Sicurezza                 | OWASP Top 10 compliance                       | SAST (SonarQube), DAST (OWASP ZAP), Dependabot, penetration test annuale  |
| Osservabilità             | 100% request traced                           | Micrometer + Prometheus, Grafana dashboards, distributed tracing (Tempo)  |
| Conformità GDPR           | Audit completo di ogni accesso ai dati        | rise_audit schema, AOP logging, GDPR Data Map aggiornata                   |
| Performance Export        | Export completo DB < 5 minuti per 1M record   | Async job, streaming JDBC cursor, compressione gzip, pre-signed URL S3     |
| Compatibilità Browser     | Chrome 120+, Firefox 121+, Edge 120+, Safari 17+ | Angular 17 Ivy, polyfills minimi, CSP headers strict                    |

---

## §8 ADR

### ADR-001 – Adozione di Spring Batch per la Pipeline di Import

**Contesto:**
Il sistema deve importare file CSV/XLSX con potenzialmente centinaia di migliaia di righe. Sono richieste riprendibilità, gestione degli errori a livello di chunk, auditing e parallelismo configurabile.

**Alternative considerate:**
- A) Implementazione custom (loop + JPA save): semplicità ma nessuna riprendibilità, OOM risk.
- B) Apache Camel: robusto ma overengineering per use case batch semplice; curva di apprendimento elevata.
- C) Spring Batch ✅: standard Spring, integrazione nativa con Spring Boot, chunk processing, job repository, restart from failure.

**Decisione:** Spring Batch 5.x con chunk size 500 e job repository su PostgreSQL.

**Trade-off:** Complessità di configurazione maggiore rispetto a soluzione custom; offset da riduzione drastica del rischio operativo e dalla riprendibilità automatica.

---

### ADR-002 – Separate Schema rise_core / rise_audit

**Contesto:**
Requisiti di compliance (GDPR, SOX) richiedono un audit trail immutabile, separato logicamente dai dati di business per prevenire modifiche accidentali o fraudolente.

**Alternative considerate:**
- A) Tabella audit nello stesso schema: semplice ma vulnerabile a DROP/UPDATE accidentali.
- B) Database separato: massimo isolamento ma overhead operativo elevato (2 istanze DB da gestire).
- C) Schema separato con role PostgreSQL dedicato ✅: isolamento logico, stesso cluster, `audit_writer` role senza UPDATE/DELETE.

**Decisione:** Schema `rise_audit` separato nel medesimo cluster PostgreSQL; il ruolo `audit_writer` ha solo INSERT.

**Trade-off:** Maggiore complessità nei migration script Flyway (due target schema); costo minimo rispetto ai benefici di compliance.

---

### ADR-003 – Cifratura pgcrypto vs. Cifratura Applicativa

**Contesto:**
I dati personali (full_name, email) devono essere protetti at-rest. La cifratura può essere realizzata a livello applicativo (Java) o a livello database (pgcrypto).

**Alternative considerate:**
- A) Cifratura applicativa (AES in Java): massima portabilità ma chiavi gestite nel codice, rischio di leak in log/heap dump.
- B) Transparent Data Encryption (TDE) PostgreSQL: cifra l'intero filesystem ma non protegge query SQL dirette sul DB.
- C) pgcrypto nel DB ✅: cifra a livello di colonna, la KEK non transita mai nelle query applicative, integrazione nativa con PostgreSQL.

**Decisione:** pgcrypto con `pgp_sym_encrypt`/`pgp_sym_decrypt`; KEK in AWS KMS con riferimento iniettato come variabile d'ambiente cifrata.

**Trade-off:** Query di ricerca su campi cifrati non possibile senza decifratura; mitigato con hash deterministico separato per lookup per employee_id.

---

### ADR-004 – SSE vs. WebSocket per Progresso Import

**Contesto:**
Il frontend deve ricevere aggiornamenti real-time sul progresso della pipeline import (percentuale completamento, errori, stato finale).

**Alternative considerate:**
- A) Polling REST ogni 2s: implementazione banale ma carico inutile sul server e latenza UI percepita.
- B) WebSocket: bidirezionale, adatto a chat/trading ma overengineering per comunicazione unidirezionale server→client.
- C) Server-Sent Events (SSE) ✅: standard HTTP, unidirezionale server→client, nativo in Angular (`EventSource`), supporto proxy HTTP/2 senza upgrade WS.

**Decisione:** SSE via endpoint `/api/v1/import/progress/{jobId}` con `Content-Type: text/event-stream`.

**Trade-off:** Nessun fallback automatico su SSE disconnect; mitigato con reconnect automatico Angular (`EventSource` ri-connette automaticamente) e stato persistito su Redis.

---

### ADR-005 – Angular 17 Standalone Components vs. NgModules

**Contesto:**
Angular 17 promuove i Standalone Components come architettura raccomandata. La scelta influenza la struttura del progetto, il lazy loading e il tree-shaking.

**Alternative considerate:**
- A) NgModules tradizionali: familiarità del team; boilerplate elevato; deprecation imminente.
- B) Standalone Components ✅: meno boilerplate, lazy loading granulare per route, migliore tree-shaking, allineamento con roadmap Angular.
- C) Micro-frontend (Module Federation): massima indipendenza dei team ma complessità infrastrutturale sproporzionata per dimensione progetto.

**Decisione:** Standalone Components + feature-based directory structure + NgRx per state management globale.

**Trade-off:** Il team richiede formazione su lazy loading standalone e `provideRouter` / `importProvidersFrom`; offset da riduzione del boilerplate e migliore manutenibilità a lungo termine.

---

## §9 Pipeline CI/CD

```
╔══════════════════════════════════════════════════════════════════════════════════╗
║                    RISE – Pipeline CI/CD Completa (GitHub Actions / GitLab CI)  ║
╚══════════════════════════════════════════════════════════════════════════════════╝

  Push a feature/* branch
           │
           ▼
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │  STAGE 1: CODE QUALITY                                                       │
  │  ─────────────────────────────────────────────────────────────────────────  │
  │  [Frontend]  npm ci → ng lint → ng test --coverage (Jest) → SonarQube scan  │
  │  [Backend]   mvn verify → Checkstyle → SpotBugs → SonarQube scan            │
  │  Threshold: coverage ≥ 80%, 0 Critical SonarQube issues → PASS / FAIL       │
  └──────────────────────────────┬──────────────────────────────────────────────┘
                                 │ PASS
                                 ▼
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │  STAGE 2: BUILD & PACKAGE                                                    │
  │  ─────────────────────────────────────────────────────────────────────────  │
  │  [Frontend]  ng build --configuration=production → dist/ artifact           │
  │  [Backend]   mvn package -DskipTests → rise-api.jar artifact                │
  │  Docker build → tag con commit SHA + branch + timestamp                     │
  │  Push image a ECR / ACR (registry privato)                                  │
  └──────────────────────────────┬──────────────────────────────────────────────┘
                                 │
                                 ▼
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │  STAGE 3: SECURITY SCAN                                                      │
  │  ─────────────────────────────────────────────────────────────────────────  │
  │  Trivy → scansione image Docker (CVE Critical/High → FAIL)                  │
  │  OWASP Dependency Check → cvss ≥ 7.0 → FAIL                                │
  │  npm audit → --audit-level=high → FAIL                                      │
  │  Secrets scan (TruffleHog / gitleaks) → FAIL su secret detected             │
  └──────────────────────────────┬──────────────────────────────────────────────┘
                                 │ PASS
                                 ▼
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │  STAGE 4: DEPLOY TO DEV                                                      │
  │  ─────────────────────────────────────────────────────────────────────────  │
  │  Helm upgrade --install rise-dev ./charts/rise --set image.tag=$SHA         │
  │  Flyway migrate (DEV schema)                                                 │
  │  Smoke test: curl /actuator/health → 200 OK                                 │
  └──────────────────────────────┬──────────────────────────────────────────────┘
                                 │
                                 ▼
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │  STAGE 5: INTEGRATION & E2E TESTS                                            │
  │  ─────────────────────────────────────────────────────────────────────────  │
  │  Spring Boot integration tests (@SpringBootTest + Testcontainers PG/Redis)  │
  │  Cypress E2E suite contro ambiente DEV (headless Chrome)                    │
  │  Performance test: k6 load test (100 VU, 5 min) → p95 < 300ms              │
  │  OWASP ZAP DAST scan against DEV API → report generato                     │
  └──────────────────────────────┬──────────────────────────────────────────────┘
                                 │ PASS
                                 ▼
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │  STAGE 6: DEPLOY TO STAGING  (solo branch main / release/*)                 │
  │  ─────────────────────────────────────────────────────────────────────────  │
  │  Helm upgrade rise-staging (blue/green deployment)                          │
  │  Flyway migrate (STAGING schema)                                             │
  │  Smoke + regression test automatici                                          │
  │  Notifica Slack: "Deploy staging completato – SHA: $SHA"                    │
  └──────────────────────────────┬──────────────────────────────────────────────┘
                                 │  Approvazione manuale (CODEOWNERS)
                                 ▼
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │  STAGE 7: DEPLOY TO PRODUCTION  (solo tag v*.*.*)                           │
  │  ─────────────────────────────────────────────────────────────────────────  │
  │  Helm upgrade rise-prod (rolling update, maxSurge=1, maxUnavailable=0)      │
  │  Flyway migrate (PROD schema) con backup preventivo automatico              │
  │  Canary release: 10% traffico → monitoraggio 15 min → 100% se OK           │
  │  Notifica PagerDuty / Slack / Teams                                         │
  │  Rollback automatico se error rate > 1% nei primi 30 min                   │
  └─────────────────────────────────────────────────────────────────────────────┘

  Artefatti conservati: Docker images (90gg), test reports (30gg), DAST reports (1 anno)
```

---

## §10 Definition of Done

La seguente checklist deve essere completamente soddisfatta prima che una User Story o un Feature Branch possa essere considerato "Done" e rilasciabile in produzione.

- [ ] **DOD-01** Il codice è stato sottoposto a Peer Review da almeno un altro sviluppatore del team tramite Pull Request.
- [ ] **DOD-02** Tutti i test unitari passano (0 failing); coverage ≥ 80% per i componenti modificati.
- [ ] **DOD-03** I test di integrazione (Testcontainers) passano in ambiente CI senza modifiche manuali.
- [ ] **DOD-04** I test E2E Cypress rilevanti allo scenario sviluppato passano nell'ambiente DEV.
- [ ] **DOD-05** Nessun issue SonarQube di severità Blocker o Critical è introdotto dalla modifica.
- [ ] **DOD-06** La scansione Trivy sull'immagine Docker non trova CVE di severità Critical.
- [ ] **DOD-07** OWASP Dependency Check non riporta vulnerabilità con CVSS ≥ 7.0 nelle dipendenze introdotte.
- [ ] **DOD-08** Le migration Flyway (se presenti) sono reversibili o documentate con piano di rollback.
- [ ] **DOD-09** Le migration hanno eseguito con successo negli ambienti DEV e STAGING.
- [ ] **DOD-10** La specifica OpenAPI (`/v3/api-docs`) è aggiornata e riflette i nuovi/modificati endpoint.
- [ ] **DOD-11** Tutti gli endpoint nuovi o modificati hanno esempi JSON documentati nella specifica.
- [ ] **DOD-12** Il RBAC è correttamente applicato: test automatici verificano accesso/negazione per ogni ruolo.
- [ ] **DOD-13** Il country scoping è verificato con test che confermano l'isolamento tra country diverse.
- [ ] **DOD-14** I campi personali (full_name, email) vengono cifrati in tutti i path di scrittura su DB.
- [ ] **DOD-15** Il masking dei dati personali nell'export è verificato da test automatici.
- [ ] **DOD-16** Gli eventi di audit sono scritti correttamente in `rise_audit.audit_events` per ogni operazione critica.
- [ ] **DOD-17** L'Import Wizard è stato testato manualmente con file CSV valido, invalido e con duplicati.
- [ ] **DOD-18** La pipeline Spring Batch gestisce correttamente entrambe le strategie SKIP e OVERWRITE.
- [ ] **DOD-19** Il progresso import via SSE è visibile e aggiornato correttamente nel frontend.
- [ ] **DOD-20** La materialized view `mv_kpi_monthly` si aggiorna correttamente al termine dell'import.
- [ ] **DOD-21** Il modello di errore standard è restituito in tutti i casi di errore (4xx, 5xx).
- [ ] **DOD-22** I log applicativi (SLF4J + JSON structured logging) non contengono dati personali in chiaro.
- [ ] **DOD-23** Le metriche Micrometer/Prometheus sono aggiornate per i nuovi componenti monitorabili.
- [ ] **DOD-24** La documentazione tecnica (README, ADR, Swagger) è aggiornata prima del merge.
- [ ] **DOD-25** Il ticket Jira/Linear associato è aggiornato con link alla PR, al deploy e all'evidence di test.
- [ ] **DOD-26** Il Product Owner ha validato la funzionalità nella Demo Review (se User Story front-facing).
- [ ] **DOD-27** Non sono presenti `TODO` o `FIXME` critici introdotti dalla modifica senza issue tracciato.

---

## ARCH_AGENT_SIGNATURE_V2

```
╔══════════════════════════════════════════════════════════════════════════════╗
║                                                                              ║
║   RISE Spending Effectiveness – Architecture & Technical Design              ║
║   Document Version: 2.0                                                      ║
║   Generation Date:  2026-03-04                                               ║
║   Produced by:      Architect Agent (AI-Agents Lab Wave 1)                  ║
║                                                                              ║
║   This document has been auto-generated by the Architect Agent and          ║
║   represents the authoritative technical specification for the RISE         ║
║   Spending Effectiveness platform. All architectural decisions recorded      ║
║   herein (ADR-001 through ADR-005) must be reviewed by the Lead             ║
║   Architect before any deviation is implemented.                             ║
║                                                                              ║
║   ARCH_AGENT_SIGNATURE_V2 – This section must be present and must           ║
║   not be removed from any distributed version of this document.             ║
║                                                                              ║
╚══════════════════════════════════════════════════════════════════════════════╝
```
