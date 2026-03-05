# RISE Spending Effectiveness – DBA Data Model
**Versione:** 2.0 | **Data:** 2026-03-04 | **Autore:** DBA Agent

---

## §1 Assunzioni

| ID | Assunzione |
|----|-----------|
| A-01 | Il database target è PostgreSQL 15 o superiore; funzionalità come colonne GENERATED ALWAYS AS STORED e EXCLUDE USING GIST sono disponibili nativamente. |
| A-02 | Le estensioni `pgcrypto` e `btree_gist` devono essere installate prima di eseguire qualsiasi migrazione DDL; entrambe sono disponibili nel repository standard di PostgreSQL contrib. |
| A-03 | Tutti i valori monetari (commissioni, bonus, target, vendite) sono espressi in valuta locale del negozio e memorizzati come `NUMERIC(18,4)` per garantire precisione decimale ad alta fedeltà senza arrotondamenti floating-point. |
| A-04 | La chiave di deduplicazione dei record mensili è la tripletta `(employee_id, shop_id, record_month, record_year)`; un dipendente non può avere più di un record per negozio per mese/anno. |
| A-05 | I dati PII (full_name, email) degli impiegati sono cifrati a livello applicativo con `pgp_sym_encrypt` (pgcrypto); la chiave simmetrica è gestita esternamente al database (es. HashiCorp Vault) e non è mai persistita nel DB. |
| A-06 | Il ruolo applicativo `rise_app` è l'unico ruolo utilizzato dall'applicazione runtime; i permessi DDL e di manutenzione sono riservati al ruolo `rise_admin`. |
| A-07 | Lo schema `rise_audit` è logicamente separato da `rise_core` per garantire l'immutabilità degli audit log; nessun processo applicativo deve poter eseguire UPDATE o DELETE su `rise_audit.audit_events`. |
| A-08 | Il soft delete è implementato tramite il campo `deleted_at TIMESTAMPTZ` nullable; i record con `deleted_at IS NOT NULL` sono considerati eliminati logicamente ma rimangono fisicamente nel database per audit e recovery. |
| A-09 | Le migrazioni sono gestite con Flyway in modalità versioned (prefix `V`); ogni migrazione è idempotente o include logica di guardia (`IF NOT EXISTS`, `DO $$ ... $$`). |
| A-10 | La Materialized View `mv_kpi_monthly` è l'unica sorgente dati per le dashboard KPI; viene refreshata tramite la funzione `fn_refresh_mv_kpi_monthly()` schedulata via pg_cron o job esterno. |
| A-11 | Il campo `employee_id` in `monthly_performance_records` è nullable per supportare la cancellazione GDPR (right-to-erasure): il record mensile viene anonimizzato impostando `employee_id = NULL` mantenendo i dati aggregati di shop. |
| A-12 | I cluster di performance (Top/Medium High/Medium/Low/Worst) sono configurabili tramite la tabella `cluster_definitions`; i seed iniziali sono inseriti nella migrazione V010. |
| A-13 | I coefficienti di costo (`cost_coefficients`) non possono avere periodi di validità sovrapposti per la stessa coppia `(country_code, coefficient_type)`; questo è garantito dal constraint `EXCLUDE USING GIST`. |
| A-14 | I timestamp `created_at` e `updated_at` sono sempre in formato `TIMESTAMPTZ` (UTC); la conversione al fuso orario locale è responsabilità del layer applicativo o di presentazione. |
| A-15 | Il campo `country_scope TEXT[]` nella tabella `users` contiene i codici ISO-2 dei paesi a cui l'utente ha accesso; un array vuoto o NULL indica nessun accesso per i ruoli non-GLOBAL_ADMIN. |
| A-16 | I template di importazione (`import_templates`) supportano mapping colonne e regole di trasformazione in formato JSONB per massima flessibilità; la validazione dello schema JSONB è responsabilità del layer applicativo. |
| A-17 | Le statistiche di importazione (rows_total, rows_valid, rows_rejected, rows_duplicate) in `import_logs` sono aggiornate incrementalmente durante l'esecuzione; il campo `status` segue una macchina a stati rigorosa. |

---

## §2 Modello ER (ASCII)

```
rise_core
═══════════════════════════════════════════════════════════════════════════════════════════

 ┌──────────────────┐          ┌────────────────────────────┐
 │   countries      │          │          shops             │
 │──────────────────│          │────────────────────────────│
 │ PK id            │◄────┐    │ PK id                      │
 │    code CHAR(2)  │     └────┤ FK country_id              │
 │    name          │          │    shop_code VARCHAR UNIQUE │
 │    region        │          │    name                    │
 │    currency_code │          │    city                    │
 │    created_at    │          │    region_code             │
 └──────────────────┘          │    is_active               │
                               │    created_at              │
                               │    updated_at              │
                               │    deleted_at              │
                               └──────────────┬─────────────┘
                                              │
              ┌───────────────────────────────┼─────────────────────────────┐
              │                               │                             │
 ┌────────────▼──────────────┐   ┌────────────▼────────────────────────────▼──────────────┐
 │        employees          │   │          monthly_performance_records                    │
 │───────────────────────────│   │────────────────────────────────────────────────────────│
 │ PK id                     │   │ PK id BIGSERIAL                                        │
 │    oracle_hcm_id UNIQUE   │   │ FK employee_id nullable (GDPR erasure)                 │
 │    full_name_enc BYTEA    │◄──┤ FK shop_id NOT NULL                                    │
 │    email_enc BYTEA        │   │    record_month SMALLINT CHECK(1-12)                   │
 │ FK shop_id                │   │    record_year  SMALLINT CHECK(2000-2100)              │
 │    hire_date              │   │    record_date  DATE GENERATED ALWAYS STORED           │
 │    termination_date       │   │    fte          NUMERIC(5,4)                           │
 │    fte  NUMERIC(5,4)      │   │    commission   NUMERIC(18,4)                          │
 │    is_active              │   │    quarterly_bonus NUMERIC(18,4)                       │
 │    created_at             │   │    annual_bonus    NUMERIC(18,4)                       │
 │    updated_at             │   │    extra_booster   NUMERIC(18,4)                       │
 │    deleted_at             │   │    other_compensation NUMERIC(18,4)                    │
 └───────────────────────────┘   │    total_sales     NUMERIC(18,4)                       │
                                 │    ha_sales         NUMERIC(18,4)                      │
                                 │    monthly_target   NUMERIC(18,4)                      │
 ┌─────────────────────────┐     │    quarterly_target NUMERIC(18,4)                      │
 │         users           │     │    annual_target    NUMERIC(18,4)                      │
 │─────────────────────────│     │    other_sales      NUMERIC(18,4)                      │
 │ PK id                   │     │ FK imported_by_log_id                                  │
 │    email UNIQUE         │     │    created_at / updated_at                             │
 │    full_name            │     │ UQ (employee_id, shop_id, record_month, record_year)   │
 │    role CHECK(...)      │     └────────────────────────────────────────────────────────┘
 │    country_scope TEXT[] │
 │    has_gdpr_access BOOL │     ┌────────────────────────────────────────────────────────┐
 │    is_active            │     │         import_logs                                    │
 │    created_at           │◄────┤─ submitted_by FK                                       │
 │    updated_at           │     │ PK id                                                  │
 │    deleted_at           │     │    country_code / file_name / data_type                │
 └────────────┬────────────┘     │ FK template_id                                         │
              │                  │    status CHECK(...)                                   │
              │                  │    rows_total/valid/rejected/duplicate                 │
 ┌────────────▼────────────┐     │    duplicate_resolution CHECK(...)                     │
 │    import_templates     │     │    started_at / completed_at / created_at              │
 │─────────────────────────│     └────────────────────┬───────────────────────────────────┘
 │ PK id                   │                          │
 │    name / country_code  │     ┌────────────────────▼───────────────────────────────────┐
 │    data_type CHECK(...)  │     │         import_log_rows                                │
 │    column_mappings JSONB │     │────────────────────────────────────────────────────────│
 │    transformation_rules  │     │ PK id BIGSERIAL                                        │
 │    version INT          │     │ FK import_log_id                                       │
 │    is_active            │     │    source_row_number INT                               │
 │ FK created_by (users)   │     │    row_status / error_code / error_message             │
 │    created_at/updated_at│     │    raw_data JSONB                                      │
 │    deleted_at           │     │    created_at                                          │
 └─────────────────────────┘     └────────────────────────────────────────────────────────┘

 ┌──────────────────────────────┐   ┌──────────────────────────────────────────────────────┐
 │     cluster_definitions      │   │              cost_coefficients                       │
 │──────────────────────────────│   │──────────────────────────────────────────────────────│
 │ PK id                        │   │ PK id                                                │
 │    cluster_name              │   │    country_code                                      │
 │    min_pct NUMERIC(5,2)      │   │    coefficient_type                                  │
 │    max_pct NUMERIC(5,2)      │   │    effective_from DATE                               │
 │    display_order INT         │   │    effective_to   DATE                               │
 │    color_hex CHAR(7)         │   │    value NUMERIC(12,6)                               │
 │    is_active                 │   │    created_at / updated_at                           │
 │    created_at / updated_at   │   │ EXCLUDE USING GIST (country_code, coefficient_type, │
 └──────────────────────────────┘   │   daterange(effective_from,effective_to,'[)') &&)   │
                                    └──────────────────────────────────────────────────────┘

rise_audit (schema separato — append-only)
═══════════════════════════════════════════════════════════════════════════════════════════

 ┌──────────────────────────────────────────────────────────┐
 │                   audit_events                           │
 │──────────────────────────────────────────────────────────│
 │ PK id BIGSERIAL                                          │
 │    event_type VARCHAR                                    │
 │    actor_user_id INT  (denormalizzato, no FK)            │
 │    actor_email VARCHAR                                   │
 │    target_entity VARCHAR                                 │
 │    target_id VARCHAR                                     │
 │    payload JSONB                                         │
 │    event_at TIMESTAMPTZ DEFAULT now()                    │
 │ [TRIGGER: blocca UPDATE/DELETE]                          │
 │ [REVOKE UPDATE, DELETE ON rise_app]                      │
 └──────────────────────────────────────────────────────────┘
```

---

## §3 DDL Completo

### §3.1 Prerequisiti e Setup

```sql
-- =============================================================================
-- RISE Spending Effectiveness - Setup Iniziale
-- PostgreSQL 15+
-- =============================================================================

-- Estensioni richieste (eseguire come superuser)
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Schema applicativo principale
CREATE SCHEMA IF NOT EXISTS rise_core;

-- Schema audit immutabile
CREATE SCHEMA IF NOT EXISTS rise_audit;

-- -------------------------------------------------------------------------
-- Ruoli
-- -------------------------------------------------------------------------

-- Ruolo di amministrazione DBA (DDL, manutenzione, REFRESH MV)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rise_admin') THEN
        CREATE ROLE rise_admin NOLOGIN;
    END IF;
END$$;

-- Ruolo applicativo runtime (solo DML controllato)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rise_app') THEN
        CREATE ROLE rise_app NOLOGIN;
    END IF;
END$$;

-- -------------------------------------------------------------------------
-- GRANT schemi
-- -------------------------------------------------------------------------

GRANT USAGE ON SCHEMA rise_core  TO rise_app;
GRANT USAGE ON SCHEMA rise_audit TO rise_app;

-- rise_app: SELECT, INSERT, UPDATE su rise_core (tranne soft-delete diretti)
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA rise_core TO rise_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA rise_core
    GRANT SELECT, INSERT, UPDATE ON TABLES TO rise_app;

-- rise_app: solo INSERT su rise_audit (NO UPDATE, NO DELETE)
GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA rise_audit TO rise_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA rise_audit
    GRANT SELECT, INSERT ON TABLES TO rise_app;

-- rise_app: USAGE sulle sequenze
GRANT USAGE ON ALL SEQUENCES IN SCHEMA rise_core  TO rise_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA rise_audit TO rise_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA rise_core
    GRANT USAGE ON SEQUENCES TO rise_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA rise_audit
    GRANT USAGE ON SEQUENCES TO rise_app;

-- rise_admin: accesso completo
GRANT ALL PRIVILEGES ON SCHEMA rise_core  TO rise_admin;
GRANT ALL PRIVILEGES ON SCHEMA rise_audit TO rise_admin;
```

---

### §3.2 rise_core.countries

```sql
-- =============================================================================
-- Tabella: rise_core.countries
-- Anagrafica paesi con codice ISO-2, regione e valuta
-- =============================================================================

CREATE TABLE IF NOT EXISTS rise_core.countries (
    id            SERIAL          PRIMARY KEY,
    code          CHAR(2)         NOT NULL,
    name          VARCHAR(100)    NOT NULL,
    region        VARCHAR(100),
    currency_code CHAR(3)         NOT NULL,
    created_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_countries_code UNIQUE (code)
);

COMMENT ON TABLE  rise_core.countries              IS 'Anagrafica paesi — codice ISO 3166-1 alpha-2';
COMMENT ON COLUMN rise_core.countries.code         IS 'Codice ISO 3166-1 alpha-2 (es. IT, DE, FR)';
COMMENT ON COLUMN rise_core.countries.currency_code IS 'Codice valuta ISO 4217 (es. EUR, USD)';
```

---

### §3.3 rise_core.shops

```sql
-- =============================================================================
-- Tabella: rise_core.shops
-- Anagrafica punti vendita con soft delete
-- =============================================================================

CREATE TABLE IF NOT EXISTS rise_core.shops (
    id          SERIAL          PRIMARY KEY,
    shop_code   VARCHAR(50)     NOT NULL,
    name        VARCHAR(200)    NOT NULL,
    country_id  INT             NOT NULL,
    city        VARCHAR(100),
    region_code VARCHAR(20),
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,

    CONSTRAINT uq_shops_shop_code  UNIQUE (shop_code),
    CONSTRAINT fk_shops_country_id FOREIGN KEY (country_id)
        REFERENCES rise_core.countries (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_shops_country_id  ON rise_core.shops (country_id);
CREATE INDEX IF NOT EXISTS idx_shops_is_active   ON rise_core.shops (is_active) WHERE deleted_at IS NULL;

COMMENT ON TABLE  rise_core.shops            IS 'Anagrafica punti vendita (negozi)';
COMMENT ON COLUMN rise_core.shops.deleted_at IS 'Soft delete: NULL = attivo, valorizzato = eliminato logicamente';
```

---

### §3.4 rise_core.employees

```sql
-- =============================================================================
-- Tabella: rise_core.employees
-- Anagrafica dipendenti con campi PII cifrati (pgcrypto)
-- full_name_enc e email_enc sono cifrati con pgp_sym_encrypt
-- =============================================================================

CREATE TABLE IF NOT EXISTS rise_core.employees (
    id                SERIAL          PRIMARY KEY,
    oracle_hcm_id     VARCHAR(100)    NOT NULL,
    full_name_enc     BYTEA           NOT NULL,  -- pgp_sym_encrypt(full_name, key)
    email_enc         BYTEA,                     -- pgp_sym_encrypt(email, key) nullable
    shop_id           INT             NOT NULL,
    hire_date         DATE            NOT NULL,
    termination_date  DATE,
    fte               NUMERIC(5,4)    NOT NULL DEFAULT 1.0000,
    is_active         BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ,

    CONSTRAINT uq_employees_oracle_hcm_id UNIQUE (oracle_hcm_id),
    CONSTRAINT fk_employees_shop_id FOREIGN KEY (shop_id)
        REFERENCES rise_core.shops (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT chk_employees_fte CHECK (fte > 0 AND fte <= 1.5000),
    CONSTRAINT chk_employees_termination_after_hire
        CHECK (termination_date IS NULL OR termination_date >= hire_date)
);

CREATE INDEX IF NOT EXISTS idx_employees_shop_id    ON rise_core.employees (shop_id);
CREATE INDEX IF NOT EXISTS idx_employees_is_active  ON rise_core.employees (is_active) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_employees_hire_date  ON rise_core.employees (hire_date);

COMMENT ON TABLE  rise_core.employees               IS 'Anagrafica dipendenti — PII cifrati con pgcrypto';
COMMENT ON COLUMN rise_core.employees.full_name_enc IS 'Nome completo cifrato: pgp_sym_encrypt(full_name::TEXT, :encryption_key)';
COMMENT ON COLUMN rise_core.employees.email_enc     IS 'Email cifrata: pgp_sym_encrypt(email::TEXT, :encryption_key) — nullable';
COMMENT ON COLUMN rise_core.employees.fte           IS 'Full-Time Equivalent: 1.0000 = tempo pieno, 0.5000 = part-time 50%';

-- Esempio di utilizzo cifratura (non parte del DDL strutturale):
-- INSERT INTO rise_core.employees (oracle_hcm_id, full_name_enc, email_enc, shop_id, hire_date)
-- VALUES (
--     'HCM-001',
--     pgp_sym_encrypt('Mario Rossi', current_setting('app.encryption_key')),
--     pgp_sym_encrypt('mario.rossi@example.com', current_setting('app.encryption_key')),
--     1, '2024-01-15'
-- );
--
-- Esempio di decifratura:
-- SELECT pgp_sym_decrypt(full_name_enc, current_setting('app.encryption_key')) AS full_name
-- FROM rise_core.employees WHERE id = 1;
```

---

### §3.5 rise_core.users

```sql
-- =============================================================================
-- Tabella: rise_core.users
-- Utenti applicativi con ruoli e scope geografico
-- =============================================================================

CREATE TABLE IF NOT EXISTS rise_core.users (
    id               SERIAL          PRIMARY KEY,
    email            VARCHAR(255)    NOT NULL,
    full_name        VARCHAR(200)    NOT NULL,
    role             VARCHAR(30)     NOT NULL,
    country_scope    TEXT[]          NOT NULL DEFAULT '{}',
    has_gdpr_access  BOOLEAN         NOT NULL DEFAULT FALSE,
    is_active        BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at       TIMESTAMPTZ,

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (
        role IN ('GLOBAL_ADMIN', 'COUNTRY_MANAGER', 'SYSTEM_ADMIN')
    )
);

CREATE INDEX IF NOT EXISTS idx_users_role       ON rise_core.users (role);
CREATE INDEX IF NOT EXISTS idx_users_is_active  ON rise_core.users (is_active) WHERE deleted_at IS NULL;
-- Indice GIN per ricerche sul array country_scope
CREATE INDEX IF NOT EXISTS idx_users_country_scope ON rise_core.users USING GIN (country_scope);

COMMENT ON TABLE  rise_core.users                  IS 'Utenti applicativi RISE con controllo accessi RBAC';
COMMENT ON COLUMN rise_core.users.role             IS 'Ruolo: GLOBAL_ADMIN | COUNTRY_MANAGER | SYSTEM_ADMIN';
COMMENT ON COLUMN rise_core.users.country_scope    IS 'Array codici ISO-2 paesi accessibili; vuoto = nessun accesso per non-GLOBAL_ADMIN';
COMMENT ON COLUMN rise_core.users.has_gdpr_access  IS 'Flag accesso dati PII in chiaro (solo utenti autorizzati DPO)';
```

---

### §3.6 rise_core.import_templates

```sql
-- =============================================================================
-- Tabella: rise_core.import_templates
-- Template configurabili per importazione file con mappature JSONB
-- =============================================================================

CREATE TABLE IF NOT EXISTS rise_core.import_templates (
    id                    SERIAL          PRIMARY KEY,
    name                  VARCHAR(200)    NOT NULL,
    country_code          CHAR(2)         NOT NULL,
    data_type             VARCHAR(20)     NOT NULL,
    column_mappings       JSONB           NOT NULL DEFAULT '{}',
    transformation_rules  JSONB           NOT NULL DEFAULT '{}',
    version               INT             NOT NULL DEFAULT 1,
    is_active             BOOLEAN         NOT NULL DEFAULT TRUE,
    created_by            INT             NOT NULL,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at            TIMESTAMPTZ,

    CONSTRAINT chk_import_templates_data_type CHECK (
        data_type IN ('BOTH', 'COMPENSATION', 'SALES')
    ),
    CONSTRAINT fk_import_templates_created_by FOREIGN KEY (created_by)
        REFERENCES rise_core.users (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT chk_import_templates_version CHECK (version >= 1)
);

CREATE INDEX IF NOT EXISTS idx_import_templates_country_code ON rise_core.import_templates (country_code);
CREATE INDEX IF NOT EXISTS idx_import_templates_data_type    ON rise_core.import_templates (data_type);
CREATE INDEX IF NOT EXISTS idx_import_templates_created_by   ON rise_core.import_templates (created_by);
-- Indice GIN per query sui JSONB
CREATE INDEX IF NOT EXISTS idx_import_templates_col_mappings
    ON rise_core.import_templates USING GIN (column_mappings);

COMMENT ON TABLE  rise_core.import_templates                       IS 'Template di importazione file per paese e tipo dato';
COMMENT ON COLUMN rise_core.import_templates.column_mappings       IS 'JSONB: mappatura colonne file sorgente -> campi DB';
COMMENT ON COLUMN rise_core.import_templates.transformation_rules  IS 'JSONB: regole di trasformazione/normalizzazione valori';
COMMENT ON COLUMN rise_core.import_templates.data_type             IS 'BOTH | COMPENSATION | SALES';
```

---

### §3.7 rise_core.import_logs

```sql
-- =============================================================================
-- Tabella: rise_core.import_logs
-- Log dei processi di importazione con contatori e stato
-- =============================================================================

CREATE TABLE IF NOT EXISTS rise_core.import_logs (
    id                    SERIAL          PRIMARY KEY,
    country_code          CHAR(2)         NOT NULL,
    file_name             VARCHAR(500)    NOT NULL,
    data_type             VARCHAR(20)     NOT NULL,
    template_id           INT,
    status                VARCHAR(30)     NOT NULL DEFAULT 'QUEUED',
    rows_total            INT             NOT NULL DEFAULT 0,
    rows_valid            INT             NOT NULL DEFAULT 0,
    rows_rejected         INT             NOT NULL DEFAULT 0,
    rows_duplicate        INT             NOT NULL DEFAULT 0,
    duplicate_resolution  VARCHAR(20)     NOT NULL DEFAULT 'SKIP',
    submitted_by          INT             NOT NULL,
    started_at            TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_import_logs_template_id FOREIGN KEY (template_id)
        REFERENCES rise_core.import_templates (id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT fk_import_logs_submitted_by FOREIGN KEY (submitted_by)
        REFERENCES rise_core.users (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT chk_import_logs_status CHECK (
        status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED')
    ),
    CONSTRAINT chk_import_logs_duplicate_resolution CHECK (
        duplicate_resolution IN ('SKIP', 'OVERWRITE')
    ),
    CONSTRAINT chk_import_logs_rows_non_negative CHECK (
        rows_total >= 0 AND rows_valid >= 0 AND rows_rejected >= 0 AND rows_duplicate >= 0
    )
);

CREATE INDEX IF NOT EXISTS idx_import_logs_country_code  ON rise_core.import_logs (country_code);
CREATE INDEX IF NOT EXISTS idx_import_logs_status        ON rise_core.import_logs (status);
CREATE INDEX IF NOT EXISTS idx_import_logs_submitted_by  ON rise_core.import_logs (submitted_by);
CREATE INDEX IF NOT EXISTS idx_import_logs_created_at    ON rise_core.import_logs (created_at DESC);

COMMENT ON TABLE  rise_core.import_logs                       IS 'Log processi importazione file — macchina a stati';
COMMENT ON COLUMN rise_core.import_logs.status               IS 'Stato: QUEUED→RUNNING→COMPLETED|COMPLETED_WITH_ERRORS|FAILED';
COMMENT ON COLUMN rise_core.import_logs.duplicate_resolution IS 'Strategia dedup: SKIP=ignora duplicati, OVERWRITE=sovrascrive';
```

---

### §3.8 rise_core.import_log_rows

```sql
-- =============================================================================
-- Tabella: rise_core.import_log_rows
-- Dettaglio riga per riga dei risultati di importazione
-- =============================================================================

CREATE TABLE IF NOT EXISTS rise_core.import_log_rows (
    id                  BIGSERIAL       PRIMARY KEY,
    import_log_id       INT             NOT NULL,
    source_row_number   INT             NOT NULL,
    row_status          VARCHAR(30)     NOT NULL,
    error_code          VARCHAR(50),
    error_message       TEXT,
    raw_data            JSONB           NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_import_log_rows_import_log_id FOREIGN KEY (import_log_id)
        REFERENCES rise_core.import_logs (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_import_log_rows_import_log_id
    ON rise_core.import_log_rows (import_log_id);
CREATE INDEX IF NOT EXISTS idx_import_log_rows_row_status
    ON rise_core.import_log_rows (import_log_id, row_status);
-- Indice GIN per query sul contenuto JSONB raw_data
CREATE INDEX IF NOT EXISTS idx_import_log_rows_raw_data
    ON rise_core.import_log_rows USING GIN (raw_data);

COMMENT ON TABLE  rise_core.import_log_rows                    IS 'Dettaglio righe importazione: stato e raw data per auditing';
COMMENT ON COLUMN rise_core.import_log_rows.source_row_number  IS 'Numero riga nel file sorgente (1-based)';
COMMENT ON COLUMN rise_core.import_log_rows.raw_data           IS 'Dati grezzi JSONB della riga sorgente per debug/reprocessing';
```

---

### §3.9 rise_core.monthly_performance_records

```sql
-- =============================================================================
-- Tabella: rise_core.monthly_performance_records
-- Record mensili di performance: compensation + sales
-- Chiave dedup: (employee_id, shop_id, record_month, record_year)
-- =============================================================================

CREATE TABLE IF NOT EXISTS rise_core.monthly_performance_records (
    id                    BIGSERIAL       PRIMARY KEY,
    employee_id           INT,                           -- nullable per GDPR right-to-erasure
    shop_id               INT             NOT NULL,
    record_month          SMALLINT        NOT NULL,
    record_year           SMALLINT        NOT NULL,
    record_date           DATE            GENERATED ALWAYS AS (
                              make_date(record_year::INT, record_month::INT, 1)
                          ) STORED,

    -- Campi Compensation
    fte                   NUMERIC(5,4)    NOT NULL DEFAULT 1.0000,
    commission            NUMERIC(18,4)   NOT NULL DEFAULT 0,
    quarterly_bonus       NUMERIC(18,4)   NOT NULL DEFAULT 0,
    annual_bonus          NUMERIC(18,4)   NOT NULL DEFAULT 0,
    extra_booster         NUMERIC(18,4)   NOT NULL DEFAULT 0,
    other_compensation    NUMERIC(18,4)   NOT NULL DEFAULT 0,

    -- Campi Sales
    total_sales           NUMERIC(18,4)   NOT NULL DEFAULT 0,
    ha_sales              NUMERIC(18,4)   NOT NULL DEFAULT 0,
    monthly_target        NUMERIC(18,4)   NOT NULL DEFAULT 0,
    quarterly_target      NUMERIC(18,4)   NOT NULL DEFAULT 0,
    annual_target         NUMERIC(18,4)   NOT NULL DEFAULT 0,
    other_sales           NUMERIC(18,4)   NOT NULL DEFAULT 0,

    -- Metadati
    imported_by_log_id    INT,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- FK
    CONSTRAINT fk_mpr_employee_id FOREIGN KEY (employee_id)
        REFERENCES rise_core.employees (id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,  -- GDPR: SET NULL anziché cascade
    CONSTRAINT fk_mpr_shop_id FOREIGN KEY (shop_id)
        REFERENCES rise_core.shops (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_mpr_imported_by_log_id FOREIGN KEY (imported_by_log_id)
        REFERENCES rise_core.import_logs (id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    -- Constraint di validità
    CONSTRAINT chk_mpr_record_month CHECK (record_month BETWEEN 1 AND 12),
    CONSTRAINT chk_mpr_record_year  CHECK (record_year  BETWEEN 2000 AND 2100),
    CONSTRAINT chk_mpr_fte          CHECK (fte > 0 AND fte <= 1.5000),
    CONSTRAINT chk_mpr_commission_nn      CHECK (commission         >= 0),
    CONSTRAINT chk_mpr_quarterly_bonus_nn CHECK (quarterly_bonus    >= 0),
    CONSTRAINT chk_mpr_annual_bonus_nn    CHECK (annual_bonus       >= 0),
    CONSTRAINT chk_mpr_extra_booster_nn   CHECK (extra_booster      >= 0),
    CONSTRAINT chk_mpr_total_sales_nn     CHECK (total_sales        >= 0),
    CONSTRAINT chk_mpr_ha_sales_nn        CHECK (ha_sales           >= 0),

    -- Chiave di deduplicazione
    CONSTRAINT uq_mpr_dedup UNIQUE (employee_id, shop_id, record_month, record_year)
);

CREATE INDEX IF NOT EXISTS idx_mpr_employee_id         ON rise_core.monthly_performance_records (employee_id);
CREATE INDEX IF NOT EXISTS idx_mpr_shop_id             ON rise_core.monthly_performance_records (shop_id);
CREATE INDEX IF NOT EXISTS idx_mpr_record_date         ON rise_core.monthly_performance_records (record_date DESC);
CREATE INDEX IF NOT EXISTS idx_mpr_year_month          ON rise_core.monthly_performance_records (record_year, record_month);
CREATE INDEX IF NOT EXISTS idx_mpr_imported_by_log_id  ON rise_core.monthly_performance_records (imported_by_log_id);

COMMENT ON TABLE  rise_core.monthly_performance_records              IS 'Record mensili performance: compensation + sales per dipendente/negozio';
COMMENT ON COLUMN rise_core.monthly_performance_records.employee_id IS 'NULL dopo GDPR right-to-erasure; record aggregato shop rimane';
COMMENT ON COLUMN rise_core.monthly_performance_records.record_date IS 'Data generata: make_date(year, month, 1) — primo giorno del mese';
COMMENT ON COLUMN rise_core.monthly_performance_records.fte         IS 'FTE del mese: può differire dal FTE corrente del dipendente';
COMMENT ON COLUMN rise_core.monthly_performance_records.ha_sales    IS 'HA Sales: vendite segmento High Availability/Healthcare';
```

---

### §3.10 rise_core.cluster_definitions

```sql
-- =============================================================================
-- Tabella: rise_core.cluster_definitions
-- Definizione cluster di performance (configurabile)
-- =============================================================================

CREATE TABLE IF NOT EXISTS rise_core.cluster_definitions (
    id             SERIAL          PRIMARY KEY,
    cluster_name   VARCHAR(100)    NOT NULL,
    min_pct        NUMERIC(5,2)    NOT NULL,
    max_pct        NUMERIC(5,2),           -- NULL = illimitato superiore (es. Top performer)
    display_order  INT             NOT NULL,
    color_hex      CHAR(7)         NOT NULL,
    is_active      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_cluster_min_pct   CHECK (min_pct >= 0),
    CONSTRAINT chk_cluster_max_pct   CHECK (max_pct IS NULL OR max_pct >= min_pct),
    CONSTRAINT chk_cluster_color_hex CHECK (color_hex ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT uq_cluster_name       UNIQUE (cluster_name),
    CONSTRAINT uq_cluster_order      UNIQUE (display_order)
);

-- Seed: 5 cluster standard
INSERT INTO rise_core.cluster_definitions
    (cluster_name, min_pct, max_pct, display_order, color_hex, is_active)
VALUES
    ('Top',          120.00, NULL,   1, '#1A9641', TRUE),
    ('Medium High',  100.00, 119.99, 2, '#A6D96A', TRUE),
    ('Medium',        80.00,  99.99, 3, '#FFFFBF', TRUE),
    ('Low',           60.00,  79.99, 4, '#FDAE61', TRUE),
    ('Worst',          0.00,  59.99, 5, '#D7191C', TRUE)
ON CONFLICT (cluster_name) DO NOTHING;

COMMENT ON TABLE  rise_core.cluster_definitions             IS 'Cluster di performance — soglie percentuali target achievement';
COMMENT ON COLUMN rise_core.cluster_definitions.min_pct    IS 'Soglia minima (inclusa) target achievement %';
COMMENT ON COLUMN rise_core.cluster_definitions.max_pct    IS 'Soglia massima (inclusa); NULL = nessun limite superiore';
COMMENT ON COLUMN rise_core.cluster_definitions.color_hex  IS 'Colore HEX per visualizzazione dashboard (es. #1A9641)';
```

---

### §3.11 rise_core.cost_coefficients

```sql
-- =============================================================================
-- Tabella: rise_core.cost_coefficients
-- Coefficienti di costo per paese e tipo — no sovrapposizioni temporali
-- EXCLUDE USING GIST garantisce non-overlap per periodo di validità
-- =============================================================================

CREATE TABLE IF NOT EXISTS rise_core.cost_coefficients (
    id               SERIAL          PRIMARY KEY,
    country_code     CHAR(2)         NOT NULL,
    coefficient_type VARCHAR(100)    NOT NULL,
    effective_from   DATE            NOT NULL,
    effective_to     DATE,                       -- NULL = ancora in vigore
    value            NUMERIC(12,6)   NOT NULL,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_cost_coeff_dates CHECK (
        effective_to IS NULL OR effective_to > effective_from
    ),
    CONSTRAINT chk_cost_coeff_value_positive CHECK (value > 0),

    -- Constraint di non-sovrapposizione temporale per (country_code, coefficient_type)
    CONSTRAINT excl_cost_coefficients_no_overlap
        EXCLUDE USING GIST (
            country_code     WITH =,
            coefficient_type WITH =,
            daterange(effective_from, COALESCE(effective_to, '9999-12-31'::DATE), '[)') WITH &&
        )
);

CREATE INDEX IF NOT EXISTS idx_cost_coeff_country_type
    ON rise_core.cost_coefficients (country_code, coefficient_type);
CREATE INDEX IF NOT EXISTS idx_cost_coeff_effective_from
    ON rise_core.cost_coefficients (effective_from);

COMMENT ON TABLE  rise_core.cost_coefficients                  IS 'Coefficienti di costo per paese — senza sovrapposizioni temporali';
COMMENT ON COLUMN rise_core.cost_coefficients.effective_to     IS 'NULL = coefficiente ancora valido (open-ended)';
COMMENT ON COLUMN rise_core.cost_coefficients.value            IS 'Valore moltiplicatore/coefficiente NUMERIC(12,6)';
```

---

### §3.12 rise_audit.audit_events

```sql
-- =============================================================================
-- Tabella: rise_audit.audit_events
-- Log audit immutabile — solo append, nessun UPDATE/DELETE
-- =============================================================================

CREATE TABLE IF NOT EXISTS rise_audit.audit_events (
    id              BIGSERIAL       PRIMARY KEY,
    event_type      VARCHAR(100)    NOT NULL,
    actor_user_id   INT,            -- denormalizzato: no FK per preservare storia dopo cancellazione utente
    actor_email     VARCHAR(255),
    target_entity   VARCHAR(100)    NOT NULL,
    target_id       VARCHAR(100)    NOT NULL,
    payload         JSONB           NOT NULL DEFAULT '{}',
    event_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_events_event_type    ON rise_audit.audit_events (event_type);
CREATE INDEX IF NOT EXISTS idx_audit_events_actor_user_id ON rise_audit.audit_events (actor_user_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_target        ON rise_audit.audit_events (target_entity, target_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_event_at      ON rise_audit.audit_events (event_at DESC);
-- Indice GIN sul payload JSONB per ricerche analitiche
CREATE INDEX IF NOT EXISTS idx_audit_events_payload
    ON rise_audit.audit_events USING GIN (payload);

-- -------------------------------------------------------------------------
-- Trigger immutabilità: blocca UPDATE e DELETE a livello DB
-- (difesa in profondità anche oltre il REVOKE applicativo)
-- -------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION rise_audit.fn_audit_events_immutable()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    RAISE EXCEPTION
        'Operazione non consentita: audit_events è immutabile. '
        'Operazione: %. Utente: %. Timestamp: %.',
        TG_OP, SESSION_USER, NOW()
        USING ERRCODE = 'restrict_violation';
    RETURN NULL;
END;
$$;

CREATE OR REPLACE TRIGGER trg_audit_events_no_update
    BEFORE UPDATE ON rise_audit.audit_events
    FOR EACH ROW
    EXECUTE FUNCTION rise_audit.fn_audit_events_immutable();

CREATE OR REPLACE TRIGGER trg_audit_events_no_delete
    BEFORE DELETE ON rise_audit.audit_events
    FOR EACH ROW
    EXECUTE FUNCTION rise_audit.fn_audit_events_immutable();

-- -------------------------------------------------------------------------
-- REVOKE esplicito su rise_app (doppio strato di protezione)
-- -------------------------------------------------------------------------
REVOKE UPDATE ON rise_audit.audit_events FROM rise_app;
REVOKE DELETE ON rise_audit.audit_events FROM rise_app;

COMMENT ON TABLE  rise_audit.audit_events               IS 'Audit log immutabile — append-only; UPDATE e DELETE bloccati da trigger e REVOKE';
COMMENT ON COLUMN rise_audit.audit_events.actor_user_id IS 'Denormalizzato: no FK per preservare la storia dopo eliminazione utenti';
COMMENT ON COLUMN rise_audit.audit_events.payload       IS 'JSONB: snapshot stato before/after per eventi di modifica';
```

---

## §4 Materialized View mv_kpi_monthly

```sql
-- =============================================================================
-- Materialized View: rise_core.mv_kpi_monthly
-- KPI mensili aggregati per negozio, con metriche efficiency e cluster
-- =============================================================================

CREATE MATERIALIZED VIEW IF NOT EXISTS rise_core.mv_kpi_monthly AS
SELECT
    -- Dimensioni temporali
    mpr.record_year,
    mpr.record_month,
    mpr.record_date,

    -- Dimensioni geografiche
    c.id                                                        AS country_id,
    c.code                                                      AS country_code,
    c.region                                                    AS country_region,

    -- Dimensioni negozio
    s.id                                                        AS shop_id,
    s.shop_code,

    -- Metriche headcount e FTE
    COUNT(mpr.employee_id)                                      AS headcount,
    COALESCE(SUM(mpr.fte), 0)                                   AS total_fte,

    -- Metriche compensation
    COALESCE(SUM(mpr.commission),          0)                   AS total_commission,
    COALESCE(SUM(mpr.quarterly_bonus),     0)                   AS total_quarterly_bonus,
    COALESCE(SUM(mpr.annual_bonus),        0)                   AS total_annual_bonus,
    COALESCE(SUM(mpr.extra_booster),       0)                   AS total_extra_booster,
    COALESCE(SUM(mpr.other_compensation),  0)                   AS total_other_compensation,
    COALESCE(
        SUM(mpr.commission + mpr.quarterly_bonus + mpr.annual_bonus
            + mpr.extra_booster + mpr.other_compensation),
        0
    )                                                           AS total_variable,

    -- Metriche vendite
    COALESCE(SUM(mpr.total_sales),         0)                   AS total_sales,
    COALESCE(SUM(mpr.ha_sales),            0)                   AS total_ha_sales,
    COALESCE(SUM(mpr.monthly_target),      0)                   AS total_monthly_target,
    COALESCE(SUM(mpr.quarterly_target),    0)                   AS total_quarterly_target,
    COALESCE(SUM(mpr.annual_target),       0)                   AS total_annual_target,

    -- KPI derivati
    CASE
        WHEN COALESCE(SUM(mpr.total_sales), 0) = 0 THEN NULL
        ELSE ROUND(
            (COALESCE(SUM(mpr.commission + mpr.quarterly_bonus + mpr.annual_bonus
                       + mpr.extra_booster + mpr.other_compensation), 0)
             / SUM(mpr.total_sales)) * 100,
            4
        )
    END                                                         AS compensation_efficiency,

    CASE
        WHEN COALESCE(SUM(mpr.total_sales), 0) = 0 THEN NULL
        ELSE ROUND(
            (COALESCE(SUM(mpr.commission + mpr.quarterly_bonus + mpr.annual_bonus
                       + mpr.extra_booster + mpr.other_compensation), 0)
             / SUM(mpr.total_sales)) * 100,
            4
        )
    END                                                         AS cost_to_revenue_pct,

    CASE
        WHEN COALESCE(SUM(mpr.monthly_target), 0) = 0 THEN NULL
        ELSE ROUND(
            (COALESCE(SUM(mpr.total_sales), 0) / SUM(mpr.monthly_target)) * 100,
            4
        )
    END                                                         AS target_achievement_pct

FROM rise_core.monthly_performance_records mpr
JOIN rise_core.shops     s ON s.id = mpr.shop_id
JOIN rise_core.countries c ON c.id = s.country_id
WHERE s.deleted_at IS NULL
GROUP BY
    mpr.record_year,
    mpr.record_month,
    mpr.record_date,
    c.id,
    c.code,
    c.region,
    s.id,
    s.shop_code
WITH DATA;

-- -------------------------------------------------------------------------
-- Indice UNIQUE sulla MV (supporta REFRESH CONCURRENTLY)
-- -------------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uidx_mv_kpi_monthly_pk
    ON rise_core.mv_kpi_monthly (record_year, record_month, shop_id);

CREATE INDEX IF NOT EXISTS idx_mv_kpi_monthly_country_code
    ON rise_core.mv_kpi_monthly (country_code, record_year, record_month);

CREATE INDEX IF NOT EXISTS idx_mv_kpi_monthly_record_date
    ON rise_core.mv_kpi_monthly (record_date DESC);

-- -------------------------------------------------------------------------
-- Funzione di refresh (chiamare da pg_cron o job esterno)
-- -------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION rise_core.fn_refresh_mv_kpi_monthly(
    p_concurrent BOOLEAN DEFAULT TRUE
)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_start    TIMESTAMPTZ := clock_timestamp();
    v_end      TIMESTAMPTZ;
    v_duration INTERVAL;
BEGIN
    IF p_concurrent THEN
        REFRESH MATERIALIZED VIEW CONCURRENTLY rise_core.mv_kpi_monthly;
    ELSE
        REFRESH MATERIALIZED VIEW rise_core.mv_kpi_monthly;
    END IF;

    v_end      := clock_timestamp();
    v_duration := v_end - v_start;

    -- Log del refresh in audit
    INSERT INTO rise_audit.audit_events
        (event_type, actor_user_id, actor_email, target_entity, target_id, payload)
    VALUES (
        'MV_REFRESH',
        NULL,
        'system@rise-internal',
        'mv_kpi_monthly',
        'rise_core.mv_kpi_monthly',
        jsonb_build_object(
            'mode',          CASE WHEN p_concurrent THEN 'CONCURRENT' ELSE 'FULL' END,
            'started_at',    v_start,
            'completed_at',  v_end,
            'duration_ms',   EXTRACT(MILLISECONDS FROM v_duration)
        )
    );

    RETURN FORMAT('MV refreshed in %s ms (mode: %s)',
        EXTRACT(MILLISECONDS FROM v_duration)::BIGINT,
        CASE WHEN p_concurrent THEN 'CONCURRENT' ELSE 'FULL' END
    );
END;
$$;

COMMENT ON MATERIALIZED VIEW rise_core.mv_kpi_monthly IS
    'KPI mensili aggregati per shop/paese — refreshare con fn_refresh_mv_kpi_monthly()';
COMMENT ON FUNCTION rise_core.fn_refresh_mv_kpi_monthly IS
    'Refresh MV KPI mensili. p_concurrent=TRUE usa CONCURRENTLY (default, no lock)';
```

---

## §5 Strategia Indici

| # | Tabella | Nome Indice | Tipo | Colonne | Scopo |
|---|---------|-------------|------|---------|-------|
| 1 | `rise_core.countries` | `uq_countries_code` | UNIQUE B-Tree | `code` | Lookup ISO-2 e FK da shops |
| 2 | `rise_core.shops` | `uq_shops_shop_code` | UNIQUE B-Tree | `shop_code` | Lookup codice negozio univoco |
| 3 | `rise_core.shops` | `idx_shops_country_id` | B-Tree | `country_id` | JOIN con countries, filtri per paese |
| 4 | `rise_core.shops` | `idx_shops_is_active` | Partial B-Tree | `is_active WHERE deleted_at IS NULL` | Filtro negozi attivi (esclude soft-deleted) |
| 5 | `rise_core.employees` | `uq_employees_oracle_hcm_id` | UNIQUE B-Tree | `oracle_hcm_id` | Match import HCM, upsert record |
| 6 | `rise_core.employees` | `idx_employees_shop_id` | B-Tree | `shop_id` | JOIN con shops, aggregati per negozio |
| 7 | `rise_core.employees` | `idx_employees_is_active` | Partial B-Tree | `is_active WHERE deleted_at IS NULL` | Filtro dipendenti attivi |
| 8 | `rise_core.employees` | `idx_employees_hire_date` | B-Tree | `hire_date` | Range queries per data assunzione |
| 9 | `rise_core.users` | `uq_users_email` | UNIQUE B-Tree | `email` | Autenticazione, lookup per email |
| 10 | `rise_core.users` | `idx_users_role` | B-Tree | `role` | Filtro per ruolo RBAC |
| 11 | `rise_core.users` | `idx_users_country_scope` | GIN | `country_scope` | Ricerca `@>` e `&&` su array paesi |
| 12 | `rise_core.import_templates` | `idx_import_templates_country_code` | B-Tree | `country_code` | Filtro template per paese |
| 13 | `rise_core.import_templates` | `idx_import_templates_col_mappings` | GIN | `column_mappings` | Query JSONB su chiavi mapping |
| 14 | `rise_core.import_logs` | `idx_import_logs_status` | B-Tree | `status` | Polling job in stato QUEUED/RUNNING |
| 15 | `rise_core.import_logs` | `idx_import_logs_created_at` | B-Tree DESC | `created_at DESC` | Elenco importazioni recenti (dashboard) |
| 16 | `rise_core.import_log_rows` | `idx_import_log_rows_import_log_id` | B-Tree | `import_log_id` | JOIN dettaglio righe per log |
| 17 | `rise_core.import_log_rows` | `idx_import_log_rows_row_status` | B-Tree | `import_log_id, row_status` | Filtro righe rifiutate/duplicate per log |
| 18 | `rise_core.import_log_rows` | `idx_import_log_rows_raw_data` | GIN | `raw_data` | Ricerca testo/chiavi nei dati grezzi |
| 19 | `rise_core.monthly_performance_records` | `uq_mpr_dedup` | UNIQUE B-Tree | `employee_id, shop_id, record_month, record_year` | Constraint deduplicazione, upsert conflict |
| 20 | `rise_core.monthly_performance_records` | `idx_mpr_record_date` | B-Tree DESC | `record_date DESC` | Range queries per data, ordinamento recenti |
| 21 | `rise_core.monthly_performance_records` | `idx_mpr_year_month` | B-Tree | `record_year, record_month` | Aggregazioni YoY e MoM |
| 22 | `rise_core.monthly_performance_records` | `idx_mpr_shop_id` | B-Tree | `shop_id` | Aggregazioni per negozio |
| 23 | `rise_core.cost_coefficients` | `excl_cost_coefficients_no_overlap` | GiST | `country_code, coefficient_type, daterange(...)` | Constraint EXCLUDE non-overlap temporale |
| 24 | `rise_core.cost_coefficients` | `idx_cost_coeff_country_type` | B-Tree | `country_code, coefficient_type` | Lookup coefficiente per paese e tipo |
| 25 | `rise_audit.audit_events` | `idx_audit_events_event_at` | B-Tree DESC | `event_at DESC` | Query audit recenti, retention scans |
| 26 | `rise_audit.audit_events` | `idx_audit_events_target` | B-Tree | `target_entity, target_id` | Audit trail per entità specifica |
| 27 | `rise_audit.audit_events` | `idx_audit_events_payload` | GIN | `payload` | Ricerca event payload JSONB |
| 28 | `rise_core.mv_kpi_monthly` | `uidx_mv_kpi_monthly_pk` | UNIQUE B-Tree | `record_year, record_month, shop_id` | Supporto REFRESH CONCURRENTLY |
| 29 | `rise_core.mv_kpi_monthly` | `idx_mv_kpi_monthly_country_code` | B-Tree | `country_code, record_year, record_month` | Filtro dashboard per paese e periodo |

---

## §6 Query Critiche

### Q1 — KPI Summary via MV (filtro anno/paese con aggregati)

```sql
-- =============================================================================
-- Q1: KPI Summary mensile per paese e anno
-- Sorgente: mv_kpi_monthly (pre-aggregata per performance)
-- Parametri: :p_country_code, :p_year
-- =============================================================================

SELECT
    mv.record_year,
    mv.record_month,
    mv.record_date,
    mv.country_code,
    mv.country_region,

    -- Aggregati negozio
    COUNT(DISTINCT mv.shop_id)                                          AS negozi_attivi,
    SUM(mv.headcount)                                                   AS headcount_totale,
    SUM(mv.total_fte)                                                   AS fte_totale,

    -- Aggregati compensation
    SUM(mv.total_commission)                                            AS commissioni_totali,
    SUM(mv.total_quarterly_bonus)                                       AS bonus_trimestrale_totale,
    SUM(mv.total_annual_bonus)                                          AS bonus_annuale_totale,
    SUM(mv.total_extra_booster)                                         AS extra_booster_totale,
    SUM(mv.total_other_compensation)                                    AS altro_compensation_totale,
    SUM(mv.total_variable)                                              AS compensazione_variabile_totale,

    -- Aggregati vendite
    SUM(mv.total_sales)                                                 AS vendite_totali,
    SUM(mv.total_ha_sales)                                              AS vendite_ha_totali,
    SUM(mv.total_monthly_target)                                        AS target_mensile_totale,

    -- KPI effectiveness
    CASE
        WHEN SUM(mv.total_sales) = 0 THEN NULL
        ELSE ROUND(SUM(mv.total_variable) / SUM(mv.total_sales) * 100, 2)
    END                                                                 AS compensation_efficiency_pct,

    CASE
        WHEN SUM(mv.total_monthly_target) = 0 THEN NULL
        ELSE ROUND(SUM(mv.total_sales) / SUM(mv.total_monthly_target) * 100, 2)
    END                                                                 AS target_achievement_pct

FROM rise_core.mv_kpi_monthly mv
WHERE mv.country_code = :p_country_code     -- es. 'IT'
  AND mv.record_year  = :p_year             -- es. 2025
GROUP BY
    mv.record_year,
    mv.record_month,
    mv.record_date,
    mv.country_code,
    mv.country_region
ORDER BY
    mv.record_year  ASC,
    mv.record_month ASC;
```

---

### Q2 — Assegnazione Cluster con pgp_sym_decrypt GDPR-aware

```sql
-- =============================================================================
-- Q2: Assegnazione cluster di performance con decifratura PII GDPR-aware
-- Mostra i dati PII in chiaro SOLO se l'utente ha has_gdpr_access = TRUE
-- Parametri: :p_record_year, :p_record_month, :p_user_id, :p_encryption_key
-- =============================================================================

WITH user_access AS (
    -- Recupera flag accesso GDPR per l'utente corrente
    SELECT has_gdpr_access
    FROM   rise_core.users
    WHERE  id = :p_user_id
      AND  is_active  = TRUE
      AND  deleted_at IS NULL
),
performance_data AS (
    SELECT
        mpr.id,
        mpr.employee_id,
        mpr.shop_id,
        mpr.record_year,
        mpr.record_month,
        s.shop_code,
        c.code                                                    AS country_code,
        mpr.fte,
        mpr.total_sales,
        mpr.monthly_target,
        CASE
            WHEN mpr.monthly_target = 0 THEN 0
            ELSE ROUND(mpr.total_sales / mpr.monthly_target * 100, 2)
        END                                                       AS achievement_pct,
        mpr.commission + mpr.quarterly_bonus + mpr.annual_bonus
            + mpr.extra_booster + mpr.other_compensation          AS total_variable_comp
    FROM  rise_core.monthly_performance_records mpr
    JOIN  rise_core.shops     s ON s.id = mpr.shop_id
    JOIN  rise_core.countries c ON c.id = s.country_id
    WHERE mpr.record_year  = :p_record_year
      AND mpr.record_month = :p_record_month
      AND s.deleted_at IS NULL
)
SELECT
    pd.id                                                         AS record_id,
    pd.shop_code,
    pd.country_code,
    pd.record_year,
    pd.record_month,

    -- Decifratura PII condizionale (GDPR-aware)
    CASE
        WHEN (SELECT has_gdpr_access FROM user_access)
        THEN pgp_sym_decrypt(e.full_name_enc::BYTEA, :p_encryption_key)
        ELSE '*** ANONIMIZZATO ***'
    END                                                           AS nome_dipendente,
    CASE
        WHEN (SELECT has_gdpr_access FROM user_access) AND e.email_enc IS NOT NULL
        THEN pgp_sym_decrypt(e.email_enc::BYTEA, :p_encryption_key)
        ELSE '*** ANONIMIZZATO ***'
    END                                                           AS email_dipendente,

    pd.fte,
    pd.total_sales,
    pd.monthly_target,
    pd.achievement_pct,
    pd.total_variable_comp,

    -- Assegnazione cluster
    cd.cluster_name,
    cd.color_hex                                                  AS cluster_colore

FROM  performance_data pd
LEFT  JOIN rise_core.employees          e  ON e.id = pd.employee_id
LEFT  JOIN rise_core.cluster_definitions cd
        ON pd.achievement_pct >= cd.min_pct
       AND (cd.max_pct IS NULL OR pd.achievement_pct <= cd.max_pct)
       AND cd.is_active = TRUE
ORDER BY
    pd.achievement_pct DESC,
    pd.shop_code;
```

---

### Q3 — Trend YoY con Funzione LAG Window

```sql
-- =============================================================================
-- Q3: Trend Year-over-Year con funzione finestra LAG
-- Confronto mensile vendite e compensation vs stesso mese anno precedente
-- Parametri: :p_country_code, :p_year_current, :p_year_prev
-- =============================================================================

WITH monthly_kpi AS (
    SELECT
        mv.record_year,
        mv.record_month,
        mv.country_code,
        mv.shop_id,
        mv.shop_code,
        mv.total_sales,
        mv.total_variable,
        mv.total_monthly_target,
        mv.target_achievement_pct,
        mv.compensation_efficiency
    FROM  rise_core.mv_kpi_monthly mv
    WHERE mv.country_code = :p_country_code
      AND mv.record_year  IN (:p_year_current, :p_year_prev)
),
shop_monthly_agg AS (
    SELECT
        record_year,
        record_month,
        country_code,
        SUM(total_sales)          AS vendite_totali,
        SUM(total_variable)       AS compensation_totale,
        SUM(total_monthly_target) AS target_totale,
        CASE
            WHEN SUM(total_monthly_target) = 0 THEN NULL
            ELSE ROUND(SUM(total_sales) / SUM(total_monthly_target) * 100, 2)
        END                       AS achievement_pct
    FROM  monthly_kpi
    GROUP BY record_year, record_month, country_code
)
SELECT
    sma.record_year,
    sma.record_month,
    sma.country_code,
    sma.vendite_totali,
    sma.compensation_totale,
    sma.target_totale,
    sma.achievement_pct,

    -- Valori anno precedente (LAG con offset per anno, stesso mese)
    LAG(sma.vendite_totali,       1) OVER (
        PARTITION BY sma.country_code, sma.record_month
        ORDER BY sma.record_year
    )                                                             AS vendite_anno_prec,

    LAG(sma.compensation_totale,  1) OVER (
        PARTITION BY sma.country_code, sma.record_month
        ORDER BY sma.record_year
    )                                                             AS compensation_anno_prec,

    LAG(sma.achievement_pct,      1) OVER (
        PARTITION BY sma.country_code, sma.record_month
        ORDER BY sma.record_year
    )                                                             AS achievement_anno_prec,

    -- Delta YoY assoluto
    sma.vendite_totali - LAG(sma.vendite_totali, 1) OVER (
        PARTITION BY sma.country_code, sma.record_month
        ORDER BY sma.record_year
    )                                                             AS delta_vendite_yoy,

    -- Delta YoY percentuale
    CASE
        WHEN LAG(sma.vendite_totali, 1) OVER (
                 PARTITION BY sma.country_code, sma.record_month
                 ORDER BY sma.record_year
             ) IS NULL
          OR LAG(sma.vendite_totali, 1) OVER (
                 PARTITION BY sma.country_code, sma.record_month
                 ORDER BY sma.record_year
             ) = 0
        THEN NULL
        ELSE ROUND(
            (sma.vendite_totali - LAG(sma.vendite_totali, 1) OVER (
                PARTITION BY sma.country_code, sma.record_month
                ORDER BY sma.record_year
            )) / LAG(sma.vendite_totali, 1) OVER (
                PARTITION BY sma.country_code, sma.record_month
                ORDER BY sma.record_year
            ) * 100,
            2
        )
    END                                                           AS crescita_yoy_pct

FROM  shop_monthly_agg sma
ORDER BY
    sma.record_year  ASC,
    sma.record_month ASC;
```

---

### Q4 — Export Completo con Masking GDPR

```sql
-- =============================================================================
-- Q4: Export completo record mensili con masking GDPR condizionale
-- I dati PII sono decifrati solo se l'utente richiedente ha has_gdpr_access=TRUE
-- Record anonimizzati (employee_id NULL) sono inclusi con indicatore
-- Parametri: :p_user_id, :p_country_code, :p_year, :p_month, :p_encryption_key
-- =============================================================================

WITH requesting_user AS (
    SELECT
        u.id,
        u.role,
        u.has_gdpr_access,
        u.country_scope
    FROM  rise_core.users u
    WHERE u.id         = :p_user_id
      AND u.is_active  = TRUE
      AND u.deleted_at IS NULL
),
access_check AS (
    SELECT
        ru.has_gdpr_access,
        -- GLOBAL_ADMIN può vedere tutti i paesi
        CASE
            WHEN ru.role = 'GLOBAL_ADMIN' THEN TRUE
            ELSE :p_country_code = ANY(ru.country_scope)
        END AS has_country_access
    FROM  requesting_user ru
)
SELECT
    mpr.id                                                        AS record_id,
    mpr.record_year,
    mpr.record_month,
    mpr.record_date,

    -- Negozio
    s.shop_code,
    s.name                                                        AS shop_name,
    s.city,
    c.code                                                        AS country_code,
    c.name                                                        AS country_name,
    c.currency_code,

    -- Dipendente — masking condizionale GDPR
    CASE
        WHEN mpr.employee_id IS NULL              THEN 'ANONIMIZZATO (GDPR)'
        WHEN NOT (SELECT has_gdpr_access FROM access_check) THEN '*** MASCHERATO ***'
        ELSE pgp_sym_decrypt(e.full_name_enc::BYTEA, :p_encryption_key)
    END                                                           AS nome_dipendente,

    CASE
        WHEN mpr.employee_id IS NULL              THEN NULL
        WHEN NOT (SELECT has_gdpr_access FROM access_check) THEN NULL
        WHEN e.email_enc IS NULL                  THEN NULL
        ELSE pgp_sym_decrypt(e.email_enc::BYTEA, :p_encryption_key)
    END                                                           AS email_dipendente,

    CASE WHEN mpr.employee_id IS NULL THEN NULL ELSE e.oracle_hcm_id END AS oracle_hcm_id,
    CASE WHEN mpr.employee_id IS NULL THEN NULL ELSE e.hire_date     END AS data_assunzione,

    -- Campi Compensation
    mpr.fte,
    mpr.commission,
    mpr.quarterly_bonus,
    mpr.annual_bonus,
    mpr.extra_booster,
    mpr.other_compensation,
    (mpr.commission + mpr.quarterly_bonus + mpr.annual_bonus
     + mpr.extra_booster + mpr.other_compensation)               AS total_variable_compensation,

    -- Campi Sales
    mpr.total_sales,
    mpr.ha_sales,
    mpr.monthly_target,
    mpr.quarterly_target,
    mpr.annual_target,
    mpr.other_sales,

    -- KPI calcolati
    CASE
        WHEN mpr.monthly_target = 0 THEN NULL
        ELSE ROUND(mpr.total_sales / mpr.monthly_target * 100, 2)
    END                                                           AS target_achievement_pct,

    -- Metadati importazione
    il.file_name                                                  AS file_sorgente,
    il.created_at                                                 AS data_importazione,

    -- Flag anonimizzazione
    (mpr.employee_id IS NULL)                                     AS is_anonimizzato,
    (SELECT has_gdpr_access FROM access_check)                    AS gdpr_access_utente

FROM  rise_core.monthly_performance_records mpr
JOIN  rise_core.shops     s  ON s.id = mpr.shop_id
JOIN  rise_core.countries c  ON c.id = s.country_id
LEFT  JOIN rise_core.employees  e  ON e.id = mpr.employee_id
LEFT  JOIN rise_core.import_logs il ON il.id = mpr.imported_by_log_id
WHERE c.code         = :p_country_code
  AND mpr.record_year  = :p_year
  AND mpr.record_month = :p_month
  AND s.deleted_at IS NULL
  -- Controllo accesso paese
  AND (SELECT has_country_access FROM access_check) = TRUE
ORDER BY
    s.shop_code,
    mpr.record_date;
```

---

## §7 Migrazioni Flyway

| Migrazione | Descrizione |
|------------|-------------|
| `V001__init_extensions_schemas.sql` | Creazione estensioni `pgcrypto` e `btree_gist`; creazione schemi `rise_core` e `rise_audit`; creazione ruoli `rise_admin` e `rise_app` |
| `V002__grant_permissions.sql` | GRANT/REVOKE permessi su schemi e tabelle per `rise_app` e `rise_admin`; REVOKE UPDATE/DELETE su `rise_audit` per `rise_app` |
| `V003__create_countries.sql` | DDL `rise_core.countries`: tabella, constraint UNIQUE su `code`, commenti |
| `V004__create_shops.sql` | DDL `rise_core.shops`: tabella, FK a `countries`, indici, soft delete, commenti |
| `V005__create_employees.sql` | DDL `rise_core.employees`: tabella con `full_name_enc BYTEA`, `email_enc BYTEA`, FK a `shops`, indici, commenti cifratura |
| `V006__create_users.sql` | DDL `rise_core.users`: tabella, CHECK role, indice GIN su `country_scope`, commenti |
| `V007__create_import_templates.sql` | DDL `rise_core.import_templates`: tabella, FK a `users`, indice GIN su `column_mappings`, commenti |
| `V008__create_import_logs.sql` | DDL `rise_core.import_logs`: tabella, CHECK status e duplicate_resolution, FK a `import_templates` e `users`, indici |
| `V009__create_import_log_rows.sql` | DDL `rise_core.import_log_rows`: tabella BIGSERIAL, FK a `import_logs` con CASCADE DELETE, indici su `row_status` e GIN su `raw_data` |
| `V010__create_monthly_performance_records.sql` | DDL `rise_core.monthly_performance_records`: tabella BIGSERIAL, colonna GENERATED ALWAYS AS `record_date`, tutti i campi compensation+sales NUMERIC(18,4), UNIQUE constraint dedup, tutti gli indici |
| `V011__create_cluster_definitions.sql` | DDL `rise_core.cluster_definitions` + INSERT seed dei 5 cluster (Top/Medium High/Medium/Low/Worst) con colori HEX |
| `V012__create_cost_coefficients.sql` | DDL `rise_core.cost_coefficients` con `EXCLUDE USING GIST` per non-overlap temporale; commenti e indici |
| `V013__create_audit_events.sql` | DDL `rise_audit.audit_events` BIGSERIAL; trigger `trg_audit_events_no_update` e `trg_audit_events_no_delete`; funzione `fn_audit_events_immutable()`; REVOKE UPDATE/DELETE da `rise_app`; indici incluso GIN su payload |
| `V014__create_mv_kpi_monthly.sql` | DDL Materialized View `rise_core.mv_kpi_monthly` con tutti i KPI; UNIQUE INDEX `uidx_mv_kpi_monthly_pk`; indici secondari; funzione `fn_refresh_mv_kpi_monthly()`; primo REFRESH |
| `V015__initial_data_and_baseline.sql` | Inserimento paesi seed (elenco Europa/MENA); inserimento coefficienti di costo baseline per paese; configurazione parametri applicativi; verifica integrità referenziale post-migrazione |

---

## §8 Policy GDPR, Retention e Masking

| Dato | Storage | Cifratura at-rest | Comportamento Export | Retention | Azione Anonimizzazione |
|------|---------|-------------------|---------------------|-----------|------------------------|
| `full_name` dipendente | `employees.full_name_enc BYTEA` | pgp_sym_encrypt (AES-256 simmetrica), chiave esterna (Vault) | Decifrato solo se `users.has_gdpr_access = TRUE`; altrimenti `'*** MASCHERATO ***'` | Durata rapporto + 10 anni (obbligo fiscale IT) | `UPDATE employees SET full_name_enc = pgp_sym_encrypt('ANONIMIZZATO', key), deleted_at = NOW() WHERE id = :id` |
| `email` dipendente | `employees.email_enc BYTEA` | pgp_sym_encrypt (AES-256 simmetrica) | Decifrato solo se `has_gdpr_access = TRUE`; altrimenti NULL | Durata rapporto + 5 anni | Come full_name: cifratura stringa placeholder + soft delete |
| `oracle_hcm_id` | `employees.oracle_hcm_id VARCHAR` | No (pseudonimo tecnico, non direttamente identificativo) | Visibile se country_access; oscurato se no accesso paese | Durata rapporto + 10 anni | `SET oracle_hcm_id = 'ANONIMIZZATO_' || id` |
| Record performance mensili | `monthly_performance_records` | Nessuna (dati aggregati/economici, non PII diretti) | Visibile con controllo paese; `employee_id NULL` se erasure eseguito | 7 anni (direttiva UE contabilità) | `UPDATE monthly_performance_records SET employee_id = NULL WHERE employee_id = :id` |
| Audit events | `rise_audit.audit_events` | Nessuna (payload può contenere metadati non-PII) | Solo `GLOBAL_ADMIN` e `SYSTEM_ADMIN` in lettura; export anonimizzato per altri | Permanente (immutabile per legge) | Non applicabile — tabella immutabile by design |
| Import log rows (raw_data) | `import_log_rows.raw_data JSONB` | Nessuna | Non esportato in produzione; accesso solo DBA/SYSTEM_ADMIN | 90 giorni post-completamento import | Purge fisico dopo retention: `DELETE FROM import_log_rows WHERE import_log_id IN (SELECT id FROM import_logs WHERE completed_at < NOW() - INTERVAL '90 days')` |
| Email utenti RISE | `users.email VARCHAR` | No (non PII dipendente, credenziale operativa) | Visibile al proprio utente e a GLOBAL_ADMIN | Durata account + 2 anni | Soft delete: `SET deleted_at = NOW(), is_active = FALSE, email = 'deleted_' || id || '@rise-deleted.internal'` |
| `hire_date` / `termination_date` | `employees` | No (data non direttamente identificativa) | Visibile solo con country_access + has_gdpr_access | Come employees | `SET hire_date = NULL, termination_date = NULL` nell'anonimizzazione |
| Column mappings template | `import_templates.column_mappings JSONB` | No | Visibile a COUNTRY_MANAGER e superiori | Illimitata (configurazione tecnica) | Non applicabile — nessun PII |
| Payload eventi import | `audit_events.payload JSONB` | No | Solo visuale tecnica; nessun PII se import corretto | Permanente | Non applicabile |

---

## DBA_AGENT_SIGNATURE_V2

```
╔══════════════════════════════════════════════════════════════════════════════╗
║           RISE SPENDING EFFECTIVENESS — DBA AGENT SIGNATURE V2             ║
╠══════════════════════════════════════════════════════════════════════════════╣
║  Documento    : RISE_DBA_DataModel.md                                       ║
║  Versione     : 2.0                                                          ║
║  Data         : 2026-03-04                                                   ║
║  Agente       : DBA Agent                                                    ║
║  Target DB    : PostgreSQL 15+                                               ║
║  Schemi       : rise_core (11 tabelle), rise_audit (1 tabella)              ║
║  Estensioni   : pgcrypto, btree_gist                                         ║
║  Ruoli        : rise_admin, rise_app                                         ║
║  Migrazioni   : V001–V015 (Flyway Versioned)                                ║
║  MV           : mv_kpi_monthly + fn_refresh_mv_kpi_monthly()               ║
║  PII          : full_name_enc BYTEA, email_enc BYTEA (pgp_sym_encrypt)      ║
║  Immutabilità : rise_audit.audit_events — trigger + REVOKE                  ║
║  Dedup Key    : employee_id + shop_id + record_month + record_year          ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

> **Nota:** Questa sezione di firma deve essere presente e non deve essere rimossa dal documento.
> Il documento copre l'intero data model RISE Spending Effectiveness su PostgreSQL 15+,
> inclusi DDL completo, indici, query critiche GDPR-aware, piano migrazioni Flyway V001–V015
> e policy di retention/masking conformi al GDPR europeo.
