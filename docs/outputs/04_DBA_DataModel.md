# RISE Spending Effectiveness – DBA Data Model

**Versione:** 2.0
**Data:** 2026-03-04
**Autore:** DBA Agent (GitHub Copilot – Claude Sonnet 4.6)
**Database:** PostgreSQL 15+
**Schemi:** `rise_core` (dati business), `rise_audit` (audit append-only)

---

## §1 Assunzioni

| ID | Assunzione |
|----|------------|
| A-01 | Il sistema gira su PostgreSQL 15 o superiore; si sfruttano le feature native di PG15 (MERGE, miglioramenti JSONB, partizioni migliorate). |
| A-02 | Le estensioni `pgcrypto` e `btree_gist` sono disponibili nel cluster e vengono installate nel database applicativo prima di qualsiasi DDL. |
| A-03 | Il ruolo applicativo `rise_app` non è un superuser; dispone esclusivamente dei privilegi `CONNECT`, `USAGE` sui due schemi e `SELECT/INSERT/UPDATE/DELETE` sulle tabelle di `rise_core` (escluse le restrizioni audit). |
| A-04 | I campi `full_name_enc` e `email_enc` degli impiegati sono cifrati con `pgp_sym_encrypt` a livello applicativo prima dell'inserimento; la chiave simmetrica non risiede nel database ma in un vault esterno (es. HashiCorp Vault) passata come parametro di sessione. |
| A-05 | Il `soft-delete` è implementato su tutte le tabelle principali tramite la colonna `deleted_at TIMESTAMPTZ`; i record con `deleted_at IS NOT NULL` sono considerati logicamente eliminati e filtrati dalla vista di default. |
| A-06 | Il valore `fte` (Full-Time Equivalent) in `monthly_performance_records` può differire dal `fte` in `employees` perché cattura il FTE effettivo del mese (possibilità di part-time temporaneo). |
| A-07 | Il vincolo `UNIQUE(employee_id, shop_id, record_month, record_year)` in `monthly_performance_records` ammette righe con `employee_id IS NULL` (record aggregati a livello shop) grazie al comportamento SQL-standard: i NULL non sono considerati uguali in un indice UNIQUE. |
| A-08 | La tabella `rise_audit.audit_events` è append-only; un trigger `BEFORE UPDATE OR DELETE` solleva un'eccezione bloccando qualsiasi modifica, e `rise_app` non ha privilegi `UPDATE`/`DELETE` su quella tabella. |
| A-09 | La `mv_kpi_monthly` viene ricaricata in modo `CONCURRENT` tramite `fn_refresh_mv_kpi_monthly()` per evitare lock esclusivi durante l'aggiornamento; è necessario che esista l'indice UNIQUE su `(record_year, record_month, shop_id)`. |
| A-10 | Le date `effective_from`/`effective_to` in `cost_coefficients` rappresentano un intervallo chiuso-aperto `[effective_from, effective_to)`, coerente con l'operatore `daterange` usato nel vincolo `EXCLUDE`. Il valore `NULL` di `effective_to` indica validità indefinita e viene mappato a `'infinity'::date` nell'espressione GIST. |
| A-11 | La colonna `country_scope TEXT[]` in `users` contiene i codici ISO-3166-1 alpha-2 dei paesi gestibili dall'utente; un array vuoto `'{}'` significa nessun accesso per scope. Un `GLOBAL_ADMIN` ignora questo filtro per convenzione applicativa. |
| A-12 | Il campo `record_date` in `monthly_performance_records` è una colonna generata (`GENERATED ALWAYS AS … STORED`) e non può essere scritta direttamente; serve come shorthand per join con range di date. |
| A-13 | Le metriche monetarie usano `NUMERIC(18,4)` per evitare errori di arrotondamento floating-point; 4 decimali sono sufficienti per valute con 2 decimali standard (EUR, USD, GBP) e per eventuali ripartizioni FTE-proporzionali. |
| A-14 | Le migrazioni vengono gestite con Flyway in modalità versioned (`V001__…sql`); il baseline è la versione V000 corrispondente al database vuoto con i soli schemi e ruoli. |
| A-15 | L'accesso GDPR-aware è governato dal campo `has_gdpr_access BOOLEAN` in `users`; solo gli utenti con questo flag `TRUE` ricevono dati in chiaro dalla `pgp_sym_decrypt`; gli altri ricevono un valore mascherato (`'***PROTECTED***'`). |

---

## §2 Modello ER ASCII

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    RISE SPENDING EFFECTIVENESS – ER DIAGRAM                  │
│                          rise_core + rise_audit                              │
└─────────────────────────────────────────────────────────────────────────────┘

┌──────────────────┐
│   countries      │
│──────────────────│
│ PK id            │
│    code CHAR(2)  │
│    name          │
│    region        │
│    currency_code │
│    created_at    │
└────────┬─────────┘
         │ 1
         │ has many
         │ N
┌────────▼─────────┐         ┌──────────────────────┐
│     shops        │◄────────┤     employees        │
│──────────────────│  shop_id│──────────────────────│
│ PK id            │         │ PK id                │
│    shop_code     │         │    oracle_hcm_id      │
│    name          │         │    full_name_enc BYTEA│
│ FK country_id    │         │    email_enc BYTEA    │
│    city          │         │ FK shop_id            │
│    region_code   │         │    hire_date          │
│    is_active     │         │    termination_date   │
│    created_at    │         │    fte                │
│    updated_at    │         │    is_active          │
│    deleted_at    │         │    created_at         │
└────────┬─────────┘         │    updated_at         │
         │                   │    deleted_at         │
         │ 1                 └──────────┬────────────┘
         │                              │
         │ N                            │ N
         │                    ┌─────────▼──────────────────────────┐
         │                    │   monthly_performance_records       │
         └───────────────────►│────────────────────────────────────│
              shop_id FK      │ PK id BIGSERIAL                    │
                              │ FK employee_id (nullable)          │
                              │ FK shop_id NOT NULL               │
                              │    record_month, record_year       │
                              │    record_date (GENERATED)         │
                              │    fte, commission                 │
                              │    quarterly_bonus, annual_bonus   │
                              │    extra_booster                   │
                              │    other_compensation              │
                              │    total_sales, ha_sales           │
                              │    monthly_target                  │
                              │    quarterly_target                │
                              │    annual_target, other_sales      │
                              │ FK imported_by_log_id              │
                              │    UNIQUE(emp,shop,month,year)     │
                              └───────────────┬────────────────────┘
                                              │ FK imported_by_log_id
                                              │
┌──────────────────┐         ┌───────────────▼────────────────────┐
│     users        │         │         import_logs                │
│──────────────────│         │────────────────────────────────────│
│ PK id            │         │ PK id                              │
│    email         │◄────────┤ FK submitted_by → users            │
│    full_name     │  subm.  │ FK template_id → import_templates  │
│    role CHECK    │         │    country_code, file_name         │
│    country_scope │         │    data_type CHECK                 │
│    has_gdpr_acc  │         │    status CHECK                    │
│    is_active     │         │    rows_total, rows_valid          │
│    created_at    │         │    rows_rejected, rows_duplicate   │
│    updated_at    │         │    duplicate_resolution CHECK      │
│    deleted_at    │         │    started_at, completed_at        │
└──────────────────┘         │    created_at                      │
         ▲                   └───────────────┬────────────────────┘
         │ created_by FK                     │ 1
┌────────┴─────────┐                         │ has many
│ import_templates │                         │ N
│──────────────────│         ┌───────────────▼────────────────────┐
│ PK id            │         │       import_log_rows              │
│    name          │         │────────────────────────────────────│
│    country_code  │         │ PK id BIGSERIAL                    │
│    data_type     │         │ FK import_log_id                   │
│    column_maps   │         │    source_row_number               │
│    transform_r   │         │    row_status, error_code          │
│    version       │         │    error_message, raw_data JSONB   │
│    is_active     │         │    created_at                      │
│    created_at    │         └────────────────────────────────────┘
│    updated_at    │
│    deleted_at    │
└──────────────────┘

┌──────────────────────────┐   ┌──────────────────────────────┐
│   cluster_definitions    │   │      cost_coefficients       │
│──────────────────────────│   │──────────────────────────────│
│ PK id                    │   │ PK id                        │
│    cluster_name          │   │    country_code CHAR(2)      │
│    min_pct NUMERIC(5,2)  │   │    coefficient_type          │
│    max_pct NUMERIC(5,2)  │   │    effective_from DATE       │
│    display_order         │   │    effective_to DATE         │
│    color_hex CHAR(7)     │   │    value NUMERIC(12,6)       │
│    is_active             │   │    created_at, updated_at    │
│    created_at, updated_at│   │    EXCLUDE USING GIST        │
└──────────────────────────┘   │    (no overlap per country   │
                               │     + type + daterange)      │
                               └──────────────────────────────┘

┌──────────────────────────────────────────────────────┐
│              rise_audit.audit_events                 │
│──────────────────────────────────────────────────────│
│ PK id BIGSERIAL                                      │
│    event_type VARCHAR(80)                            │
│    actor_user_id INT                                 │
│    actor_email VARCHAR(255)                          │
│    target_entity VARCHAR(80)                         │
│    target_id VARCHAR(100)                            │
│    payload JSONB                                     │
│    event_at TIMESTAMPTZ DEFAULT now()                │
│    [TRIGGER: immutabilità – BEFORE UPDATE OR DELETE] │
│    [REVOKE UPDATE, DELETE FROM rise_app]             │
└──────────────────────────────────────────────────────┘
```

---

## §3 DDL Completo

### §3.1 Prerequisiti

```sql
-- =============================================================
-- §3.1 PREREQUISITI – Estensioni, Schemi, Ruolo, Permessi
-- =============================================================

-- Estensioni (eseguire come superuser)
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Schemi
CREATE SCHEMA IF NOT EXISTS rise_core;
CREATE SCHEMA IF NOT EXISTS rise_audit;

-- Ruolo applicativo (senza login; il pool di connessioni usa un ruolo separato)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rise_app') THEN
        CREATE ROLE rise_app NOLOGIN;
    END IF;
END;
$$;

-- Privilegi sugli schemi
GRANT USAGE ON SCHEMA rise_core  TO rise_app;
GRANT USAGE ON SCHEMA rise_audit TO rise_app;

-- Privilegi di default per oggetti futuri in rise_core
ALTER DEFAULT PRIVILEGES IN SCHEMA rise_core
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO rise_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA rise_core
    GRANT USAGE, SELECT ON SEQUENCES TO rise_app;

-- Audit: solo INSERT e SELECT
ALTER DEFAULT PRIVILEGES IN SCHEMA rise_audit
    GRANT SELECT, INSERT ON TABLES TO rise_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA rise_audit
    GRANT USAGE, SELECT ON SEQUENCES TO rise_app;
```

---

### §3.2 `rise_core.countries`

```sql
-- =============================================================
-- §3.2 rise_core.countries
-- =============================================================
CREATE TABLE rise_core.countries (
    id            SERIAL        PRIMARY KEY,
    code          CHAR(2)       NOT NULL,
    name          VARCHAR(100)  NOT NULL,
    region        VARCHAR(50),
    currency_code CHAR(3),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_countries_code UNIQUE (code)
);

COMMENT ON TABLE  rise_core.countries            IS 'Anagrafica paesi ISO-3166-1 alpha-2.';
COMMENT ON COLUMN rise_core.countries.code       IS 'Codice ISO-3166-1 alpha-2 (es. IT, FR, DE).';
COMMENT ON COLUMN rise_core.countries.currency_code IS 'Codice valuta ISO-4217 (es. EUR, USD).';
```

---

### §3.3 `rise_core.shops`

```sql
-- =============================================================
-- §3.3 rise_core.shops
-- =============================================================
CREATE TABLE rise_core.shops (
    id           SERIAL        PRIMARY KEY,
    shop_code    VARCHAR(30)   NOT NULL,
    name         VARCHAR(200)  NOT NULL,
    country_id   INT           NOT NULL,
    city         VARCHAR(100),
    region_code  VARCHAR(20),
    is_active    BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ,
    deleted_at   TIMESTAMPTZ,

    CONSTRAINT uq_shops_code     UNIQUE      (shop_code),
    CONSTRAINT fk_shops_country  FOREIGN KEY (country_id)
                                     REFERENCES rise_core.countries(id)
                                     ON UPDATE CASCADE
                                     ON DELETE RESTRICT
);

COMMENT ON TABLE  rise_core.shops           IS 'Punti vendita (negozi) con soft-delete.';
COMMENT ON COLUMN rise_core.shops.deleted_at IS 'Valorizzato al momento del soft-delete; NULL = record attivo.';
```

---

### §3.4 `rise_core.employees`

```sql
-- =============================================================
-- §3.4 rise_core.employees
-- =============================================================
CREATE TABLE rise_core.employees (
    id               SERIAL       PRIMARY KEY,
    oracle_hcm_id    VARCHAR(50)  NOT NULL,
    full_name_enc    BYTEA        NOT NULL,   -- pgp_sym_encrypt(full_name, key)
    email_enc        BYTEA,                   -- pgp_sym_encrypt(email, key) nullable
    shop_id          INT,
    hire_date        DATE,
    termination_date DATE,
    fte              NUMERIC(5,4) NOT NULL DEFAULT 1.0,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ,

    CONSTRAINT uq_employees_hcm_id UNIQUE      (oracle_hcm_id),
    CONSTRAINT fk_employees_shop   FOREIGN KEY (shop_id)
                                       REFERENCES rise_core.shops(id)
                                       ON UPDATE CASCADE
                                       ON DELETE RESTRICT,
    CONSTRAINT ck_employees_fte    CHECK       (fte > 0 AND fte <= 1)
);

COMMENT ON TABLE  rise_core.employees               IS 'Dipendenti con PII cifrate via pgcrypto.';
COMMENT ON COLUMN rise_core.employees.full_name_enc IS 'Nome completo cifrato con pgp_sym_encrypt (chiave da vault esterno).';
COMMENT ON COLUMN rise_core.employees.email_enc     IS 'Email cifrata; NULL se non disponibile.';
```

---

### §3.5 `rise_core.users`

```sql
-- =============================================================
-- §3.5 rise_core.users
-- =============================================================
CREATE TABLE rise_core.users (
    id              SERIAL       PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    full_name       VARCHAR(200),
    role            VARCHAR(30)  NOT NULL,
    country_scope   TEXT[]       NOT NULL DEFAULT '{}',
    has_gdpr_access BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role  CHECK  (role IN ('GLOBAL_ADMIN', 'COUNTRY_MANAGER', 'SYSTEM_ADMIN'))
);

COMMENT ON TABLE  rise_core.users                    IS 'Utenti della piattaforma RISE con controllo accesso GDPR.';
COMMENT ON COLUMN rise_core.users.country_scope      IS 'Array di codici paese gestibili (vuoto = nessuno; ignorato per GLOBAL_ADMIN).';
COMMENT ON COLUMN rise_core.users.has_gdpr_access    IS 'TRUE → l''utente può vedere PII decifrate.';
```

---

### §3.6 `rise_core.import_templates`

```sql
-- =============================================================
-- §3.6 rise_core.import_templates
-- =============================================================
CREATE TABLE rise_core.import_templates (
    id                   SERIAL       PRIMARY KEY,
    name                 VARCHAR(150) NOT NULL,
    country_code         CHAR(2),
    data_type            VARCHAR(20)  NOT NULL,
    column_mappings      JSONB        NOT NULL DEFAULT '{}',
    transformation_rules JSONB        NOT NULL DEFAULT '{}',
    version              INT          NOT NULL DEFAULT 1,
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by           INT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ,
    deleted_at           TIMESTAMPTZ,

    CONSTRAINT ck_it_data_type CHECK (data_type IN ('BOTH', 'COMPENSATION', 'SALES')),
    CONSTRAINT ck_it_version   CHECK (version >= 1),
    CONSTRAINT fk_it_user      FOREIGN KEY (created_by)
                                   REFERENCES rise_core.users(id)
                                   ON UPDATE CASCADE
                                   ON DELETE SET NULL
);

COMMENT ON TABLE  rise_core.import_templates              IS 'Template di importazione CSV/XLSX configurabili per paese e tipo dato.';
COMMENT ON COLUMN rise_core.import_templates.column_mappings IS 'Mappa colonna-sorgente → campo interno in formato JSONB.';
```

---

### §3.7 `rise_core.import_logs`

```sql
-- =============================================================
-- §3.7 rise_core.import_logs
-- =============================================================
CREATE TABLE rise_core.import_logs (
    id                   SERIAL       PRIMARY KEY,
    country_code         CHAR(2)      NOT NULL,
    file_name            VARCHAR(500) NOT NULL,
    data_type            VARCHAR(20)  NOT NULL,
    template_id          INT,
    status               VARCHAR(30)  NOT NULL DEFAULT 'QUEUED',
    rows_total           INT          NOT NULL DEFAULT 0,
    rows_valid           INT          NOT NULL DEFAULT 0,
    rows_rejected        INT          NOT NULL DEFAULT 0,
    rows_duplicate       INT          NOT NULL DEFAULT 0,
    duplicate_resolution VARCHAR(20),
    submitted_by         INT,
    started_at           TIMESTAMPTZ,
    completed_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_il_data_type  CHECK (data_type IN ('BOTH', 'COMPENSATION', 'SALES')),
    CONSTRAINT ck_il_status     CHECK (status IN (
                                    'QUEUED', 'RUNNING', 'COMPLETED',
                                    'COMPLETED_WITH_ERRORS', 'FAILED')),
    CONSTRAINT ck_il_dup_res    CHECK (duplicate_resolution IS NULL
                                    OR duplicate_resolution IN ('SKIP', 'OVERWRITE')),
    CONSTRAINT ck_il_rows       CHECK (rows_total >= 0
                                    AND rows_valid >= 0
                                    AND rows_rejected >= 0
                                    AND rows_duplicate >= 0),
    CONSTRAINT fk_il_template   FOREIGN KEY (template_id)
                                    REFERENCES rise_core.import_templates(id)
                                    ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_il_user       FOREIGN KEY (submitted_by)
                                    REFERENCES rise_core.users(id)
                                    ON UPDATE CASCADE ON DELETE SET NULL
);

COMMENT ON TABLE rise_core.import_logs IS 'Registro di ogni importazione file con metadati di stato e statistiche righe.';
```

---

### §3.8 `rise_core.import_log_rows`

```sql
-- =============================================================
-- §3.8 rise_core.import_log_rows
-- =============================================================
CREATE TABLE rise_core.import_log_rows (
    id                BIGSERIAL    PRIMARY KEY,
    import_log_id     INT          NOT NULL,
    source_row_number INT          NOT NULL,
    row_status        VARCHAR(30)  NOT NULL,
    error_code        VARCHAR(60),
    error_message     TEXT,
    raw_data          JSONB,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_ilr_log FOREIGN KEY (import_log_id)
                              REFERENCES rise_core.import_logs(id)
                              ON UPDATE CASCADE ON DELETE CASCADE
) PARTITION BY RANGE (import_log_id);

-- Partizione di default (le partizioni per intervallo vengono create on-demand via Flyway)
CREATE TABLE rise_core.import_log_rows_default
    PARTITION OF rise_core.import_log_rows DEFAULT;

COMMENT ON TABLE rise_core.import_log_rows IS 'Dettaglio righe di ogni importazione; partizionata su import_log_id.';
```

---

### §3.9 `rise_core.monthly_performance_records`

```sql
-- =============================================================
-- §3.9 rise_core.monthly_performance_records
-- =============================================================
CREATE TABLE rise_core.monthly_performance_records (
    id                  BIGSERIAL     PRIMARY KEY,
    employee_id         INT,
    shop_id             INT           NOT NULL,
    record_month        SMALLINT      NOT NULL,
    record_year         SMALLINT      NOT NULL,
    record_date         DATE          GENERATED ALWAYS AS
                            (make_date(record_year::INT, record_month::INT, 1)) STORED,
    fte                 NUMERIC(5,4)  NOT NULL DEFAULT 1.0,
    commission          NUMERIC(18,4) NOT NULL DEFAULT 0,
    quarterly_bonus     NUMERIC(18,4) NOT NULL DEFAULT 0,
    annual_bonus        NUMERIC(18,4) NOT NULL DEFAULT 0,
    extra_booster       NUMERIC(18,4) NOT NULL DEFAULT 0,
    other_compensation  NUMERIC(18,4) NOT NULL DEFAULT 0,
    total_sales         NUMERIC(18,4) NOT NULL DEFAULT 0,
    ha_sales            NUMERIC(18,4) NOT NULL DEFAULT 0,
    monthly_target      NUMERIC(18,4) NOT NULL DEFAULT 0,
    quarterly_target    NUMERIC(18,4) NOT NULL DEFAULT 0,
    annual_target       NUMERIC(18,4) NOT NULL DEFAULT 0,
    other_sales         NUMERIC(18,4) NOT NULL DEFAULT 0,
    imported_by_log_id  INT,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,

    CONSTRAINT uq_mpr_key        UNIQUE      (employee_id, shop_id, record_month, record_year),
    CONSTRAINT ck_mpr_month      CHECK       (record_month BETWEEN 1 AND 12),
    CONSTRAINT ck_mpr_year       CHECK       (record_year  BETWEEN 2000 AND 2100),
    CONSTRAINT ck_mpr_fte        CHECK       (fte > 0 AND fte <= 1),
    CONSTRAINT fk_mpr_employee   FOREIGN KEY (employee_id)
                                     REFERENCES rise_core.employees(id)
                                     ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mpr_shop       FOREIGN KEY (shop_id)
                                     REFERENCES rise_core.shops(id)
                                     ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mpr_log        FOREIGN KEY (imported_by_log_id)
                                     REFERENCES rise_core.import_logs(id)
                                     ON UPDATE CASCADE ON DELETE SET NULL
);

COMMENT ON TABLE  rise_core.monthly_performance_records         IS 'Record mensili di performance (compensi + vendite) per dipendente/shop.';
COMMENT ON COLUMN rise_core.monthly_performance_records.record_date IS 'Colonna generata: primo giorno del mese di riferimento.';
```

---

### §3.10 `rise_core.cluster_definitions`

```sql
-- =============================================================
-- §3.10 rise_core.cluster_definitions
-- =============================================================
CREATE TABLE rise_core.cluster_definitions (
    id            SERIAL        PRIMARY KEY,
    cluster_name  VARCHAR(60)   NOT NULL,
    min_pct       NUMERIC(5,2)  NOT NULL,
    max_pct       NUMERIC(5,2),                -- NULL = senza limite superiore (Top)
    display_order INT           NOT NULL,
    color_hex     CHAR(7)       NOT NULL,
    is_active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ,

    CONSTRAINT ck_cd_color  CHECK (color_hex ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT ck_cd_minpct CHECK (min_pct >= 0)
);

-- Seed iniziale
INSERT INTO rise_core.cluster_definitions
    (cluster_name, min_pct, max_pct, display_order, color_hex)
VALUES
    ('Top',         130.00, NULL,   1, '#27AE60'),
    ('Medium High', 105.00, 129.99, 2, '#2ECC71'),
    ('Medium',       98.00, 104.99, 3, '#F39C12'),
    ('Low',          90.00,  97.99, 4, '#E67E22'),
    ('Worst',         0.00,  89.99, 5, '#E74C3C');

COMMENT ON TABLE rise_core.cluster_definitions IS 'Definizione dei cluster di performance con soglie percentuali; seed a 5 livelli.';
```

---

### §3.11 `rise_core.cost_coefficients`

```sql
-- =============================================================
-- §3.11 rise_core.cost_coefficients
-- =============================================================
CREATE TABLE rise_core.cost_coefficients (
    id               SERIAL        PRIMARY KEY,
    country_code     CHAR(2)       NOT NULL,
    coefficient_type VARCHAR(80)   NOT NULL,
    effective_from   DATE          NOT NULL,
    effective_to     DATE,                      -- NULL = validità indefinita
    value            NUMERIC(12,6) NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,

    CONSTRAINT ck_cc_dates CHECK (effective_to IS NULL OR effective_to > effective_from),

    -- Nessuna sovrapposizione temporale per stessa nazione + tipo coefficiente
    CONSTRAINT no_overlap EXCLUDE USING GIST (
        country_code     WITH =,
        coefficient_type WITH =,
        daterange(effective_from,
                  COALESCE(effective_to, 'infinity'::date),
                  '[)') WITH &&
    )
);

COMMENT ON TABLE  rise_core.cost_coefficients IS 'Coefficienti di costo per paese con vincolo di non-sovrapposizione GIST.';
COMMENT ON COLUMN rise_core.cost_coefficients.effective_to IS 'NULL indica validità indefinita; mappato a infinity nell''EXCLUDE.';
```

---

### §3.12 `rise_audit.audit_events` (con trigger di immutabilità e REVOKE)

```sql
-- =============================================================
-- §3.12 rise_audit.audit_events
-- =============================================================
CREATE TABLE rise_audit.audit_events (
    id             BIGSERIAL    PRIMARY KEY,
    event_type     VARCHAR(80)  NOT NULL,
    actor_user_id  INT,
    actor_email    VARCHAR(255),
    target_entity  VARCHAR(80),
    target_id      VARCHAR(100),
    payload        JSONB,
    event_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE rise_audit.audit_events IS 'Log di audit append-only. Nessuna riga può essere modificata o eliminata.';

-- ----------------------------------------------------------------
-- Trigger di immutabilità: blocca UPDATE e DELETE su audit_events
-- ----------------------------------------------------------------
CREATE OR REPLACE FUNCTION rise_audit.fn_audit_immutable()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = rise_audit, public
AS $$
BEGIN
    RAISE EXCEPTION
        'Operazione % non consentita sulla tabella audit_events (append-only). '
        'event_at=%, id=%',
        TG_OP, OLD.event_at, OLD.id
        USING ERRCODE = 'restrict_violation';
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_audit_events_immutable
BEFORE UPDATE OR DELETE ON rise_audit.audit_events
FOR EACH ROW EXECUTE FUNCTION rise_audit.fn_audit_immutable();

-- ----------------------------------------------------------------
-- Revoca privilegi UPDATE e DELETE su audit_events da rise_app
-- (e da PUBLIC per sicurezza difensiva)
-- ----------------------------------------------------------------
REVOKE UPDATE, DELETE ON rise_audit.audit_events FROM rise_app;
REVOKE UPDATE, DELETE ON rise_audit.audit_events FROM PUBLIC;

-- Solo INSERT e SELECT per rise_app
GRANT SELECT, INSERT ON rise_audit.audit_events TO rise_app;
GRANT USAGE, SELECT ON SEQUENCE rise_audit.audit_events_id_seq TO rise_app;

-- Indice per ricerche frequenti su audit
CREATE INDEX idx_audit_actor    ON rise_audit.audit_events (actor_user_id, event_at DESC);
CREATE INDEX idx_audit_entity   ON rise_audit.audit_events (target_entity, target_id, event_at DESC);
CREATE INDEX idx_audit_event_at ON rise_audit.audit_events (event_at DESC);
```

---

## §4 Materialized View `mv_kpi_monthly`

```sql
-- =============================================================
-- §4 Materialized View mv_kpi_monthly
-- =============================================================

CREATE MATERIALIZED VIEW rise_core.mv_kpi_monthly AS
SELECT
    -- Dimensioni temporali
    mpr.record_year                                          AS record_year,
    mpr.record_month                                         AS record_month,
    mpr.record_date                                          AS record_date,

    -- Dimensioni geografiche
    c.id                                                     AS country_id,
    c.code                                                   AS country_code,
    c.region                                                 AS country_region,

    -- Dimensioni shop
    s.id                                                     AS shop_id,
    s.shop_code                                              AS shop_code,

    -- Metriche headcount e FTE
    COUNT(DISTINCT mpr.employee_id)                          AS headcount,
    SUM(mpr.fte)                                             AS total_fte,

    -- Voci di compensazione variabile
    SUM(mpr.commission)                                      AS total_commission,
    SUM(mpr.quarterly_bonus)                                 AS total_quarterly_bonus,
    SUM(mpr.annual_bonus)                                    AS total_annual_bonus,
    SUM(mpr.extra_booster)                                   AS total_extra_booster,
    SUM(mpr.other_compensation)                              AS total_other_compensation,

    -- Totale variabile (somma di tutte le voci di compensazione)
    SUM(
        mpr.commission
        + mpr.quarterly_bonus
        + mpr.annual_bonus
        + mpr.extra_booster
        + mpr.other_compensation
    )                                                        AS total_variable,

    -- Metriche di vendita
    SUM(mpr.total_sales)                                     AS total_sales,
    SUM(mpr.ha_sales)                                        AS total_ha_sales,
    SUM(mpr.other_sales)                                     AS total_other_sales,

    -- Target
    SUM(mpr.monthly_target)                                  AS total_monthly_target,
    SUM(mpr.quarterly_target)                                AS total_quarterly_target,
    SUM(mpr.annual_target)                                   AS total_annual_target,

    -- KPI calcolati
    -- Efficienza compensazione: vendite generate per unità di costo variabile
    ROUND(
        SUM(mpr.total_sales)
        / NULLIF(
            SUM(mpr.commission + mpr.quarterly_bonus + mpr.annual_bonus
                + mpr.extra_booster + mpr.other_compensation),
            0
          ),
        4
    )                                                        AS compensation_efficiency,

    -- Costo variabile come % sul revenue
    ROUND(
        SUM(mpr.commission + mpr.quarterly_bonus + mpr.annual_bonus
            + mpr.extra_booster + mpr.other_compensation)
        / NULLIF(SUM(mpr.total_sales), 0)
        * 100,
        2
    )                                                        AS cost_to_revenue_pct,

    -- Achievement target mensile (%)
    ROUND(
        SUM(mpr.total_sales)
        / NULLIF(SUM(mpr.monthly_target), 0)
        * 100,
        2
    )                                                        AS target_achievement_pct

FROM rise_core.monthly_performance_records mpr
JOIN rise_core.shops    s ON s.id = mpr.shop_id
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

-- ----------------------------------------------------------------
-- Indice UNIQUE richiesto per REFRESH CONCURRENTLY
-- ----------------------------------------------------------------
CREATE UNIQUE INDEX uix_mv_kpi_monthly_key
    ON rise_core.mv_kpi_monthly (record_year, record_month, shop_id);

-- Indici aggiuntivi per performance query
CREATE INDEX idx_mv_kpi_country_period
    ON rise_core.mv_kpi_monthly (country_code, record_year, record_month);

CREATE INDEX idx_mv_kpi_record_date
    ON rise_core.mv_kpi_monthly (record_date DESC);

-- ----------------------------------------------------------------
-- Funzione di refresh
-- ----------------------------------------------------------------
CREATE OR REPLACE FUNCTION rise_core.fn_refresh_mv_kpi_monthly(
    p_concurrent BOOLEAN DEFAULT TRUE
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = rise_core, public
AS $$
DECLARE
    v_start TIMESTAMPTZ := clock_timestamp();
    v_elapsed INTERVAL;
BEGIN
    IF p_concurrent THEN
        -- Richiede l'indice UNIQUE; non acquisisce lock esclusivo sulla MV
        REFRESH MATERIALIZED VIEW CONCURRENTLY rise_core.mv_kpi_monthly;
    ELSE
        -- Refresh completo (bloccante, usare solo in manutenzione)
        REFRESH MATERIALIZED VIEW rise_core.mv_kpi_monthly;
    END IF;

    v_elapsed := clock_timestamp() - v_start;

    -- Log di audit del refresh
    INSERT INTO rise_audit.audit_events (
        event_type, actor_email, target_entity, target_id, payload
    ) VALUES (
        'MV_REFRESH',
        current_user,
        'mv_kpi_monthly',
        'rise_core.mv_kpi_monthly',
        jsonb_build_object(
            'concurrent',    p_concurrent,
            'elapsed_ms',    EXTRACT(MILLISECOND FROM v_elapsed)::INT,
            'refreshed_at',  now()
        )
    );
END;
$$;

COMMENT ON FUNCTION rise_core.fn_refresh_mv_kpi_monthly(BOOLEAN) IS
    'Ricalcola mv_kpi_monthly. Con p_concurrent=TRUE usa REFRESH CONCURRENTLY (default).';

-- Grant per rise_app (può eseguire la funzione ma non fare REFRESH diretto)
GRANT EXECUTE ON FUNCTION rise_core.fn_refresh_mv_kpi_monthly(BOOLEAN) TO rise_app;
```

---

## §5 Strategia Indici

| # | Tabella | Nome Indice | Tipo | Colonne | Scopo |
|---|---------|-------------|------|---------|-------|
| 1 | `countries` | `uq_countries_code` | UNIQUE BTREE | `code` | Lookup rapido per codice paese ISO |
| 2 | `shops` | `uq_shops_code` | UNIQUE BTREE | `shop_code` | Lookup per codice negozio (import, API) |
| 3 | `shops` | `idx_shops_country` | BTREE | `country_id, is_active` | Filtraggio negozi attivi per paese |
| 4 | `shops` | `idx_shops_deleted` | PARTIAL BTREE | `id` WHERE `deleted_at IS NULL` | Velocizza tutte le query che escludono soft-deleted |
| 5 | `employees` | `uq_employees_hcm_id` | UNIQUE BTREE | `oracle_hcm_id` | Chiave naturale per integrazione Oracle HCM |
| 6 | `employees` | `idx_emp_shop_active` | BTREE | `shop_id, is_active` | Elenco dipendenti attivi per shop |
| 7 | `employees` | `idx_emp_deleted` | PARTIAL BTREE | `id` WHERE `deleted_at IS NULL` | Filtraggio soft-delete senza overhead full-scan |
| 8 | `users` | `uq_users_email` | UNIQUE BTREE | `email` | Autenticazione, lookup per email |
| 9 | `users` | `idx_users_role_active` | BTREE | `role, is_active` | Ricerca utenti per ruolo (es. tutti i COUNTRY_MANAGER) |
| 10 | `import_templates` | `idx_it_country_type` | BTREE | `country_code, data_type, is_active` | Ricerca template attivi per paese e tipo |
| 11 | `import_logs` | `idx_il_country_status` | BTREE | `country_code, status, created_at DESC` | Monitoraggio import in corso / falliti per paese |
| 12 | `import_logs` | `idx_il_submitted_by` | BTREE | `submitted_by, created_at DESC` | Storico import per utente |
| 13 | `import_logs` | `idx_il_template` | BTREE | `template_id` | FK navigation, join con template |
| 14 | `import_log_rows` | `idx_ilr_log_status` | BTREE | `import_log_id, row_status` | Recupero righe errate/scartate per un dato import |
| 15 | `import_log_rows` | `idx_ilr_log_rownum` | BTREE | `import_log_id, source_row_number` | Ricostruzione ordinata del file sorgente |
| 16 | `monthly_performance_records` | `uq_mpr_key` | UNIQUE BTREE | `employee_id, shop_id, record_month, record_year` | Vincolo unicità + PK business |
| 17 | `monthly_performance_records` | `idx_mpr_shop_date` | BTREE | `shop_id, record_year DESC, record_month DESC` | Query per shop su range temporali |
| 18 | `monthly_performance_records` | `idx_mpr_employee_date` | BTREE | `employee_id, record_year DESC, record_month DESC` | Storico performance per dipendente |
| 19 | `monthly_performance_records` | `idx_mpr_record_date` | BTREE | `record_date DESC` | Range scan su data effettiva (generata STORED) |
| 20 | `monthly_performance_records` | `idx_mpr_log` | BTREE | `imported_by_log_id` | Tracciabilità verso import sorgente |
| 21 | `cluster_definitions` | `idx_cd_active_order` | PARTIAL BTREE | `display_order` WHERE `is_active = TRUE` | Elenco cluster attivi in ordine di visualizzazione |
| 22 | `cost_coefficients` | `idx_cc_country_type_from` | BTREE | `country_code, coefficient_type, effective_from` | Lookup coefficiente valido per data specifica |
| 23 | `cost_coefficients` | `no_overlap` | GIST | `country_code, coefficient_type, daterange(effective_from, COALESCE(effective_to,'infinity'), '[)')` | Vincolo EXCLUDE no-overlap temporale (già implicito nel DDL) |
| 24 | `audit_events` | `idx_audit_actor` | BTREE | `actor_user_id, event_at DESC` | Cronologia azioni per utente |
| 25 | `audit_events` | `idx_audit_entity` | BTREE | `target_entity, target_id, event_at DESC` | Timeline di eventi su un'entità specifica |
| 26 | `audit_events` | `idx_audit_event_at` | BTREE | `event_at DESC` | Query temporali sul log globale |
| 27 | `mv_kpi_monthly` | `uix_mv_kpi_monthly_key` | UNIQUE BTREE | `record_year, record_month, shop_id` | Abilitazione REFRESH CONCURRENTLY |
| 28 | `mv_kpi_monthly` | `idx_mv_kpi_country_period` | BTREE | `country_code, record_year, record_month` | Dashboard per paese e periodo |

---

## §6 Query Critiche

### Q1 – KPI Summary via `mv_kpi_monthly`

```sql
-- =============================================================
-- Q1: KPI Summary mensile aggregato per paese e mese
--     Utilizza la MV per evitare ricalcoli real-time
-- =============================================================

SELECT
    kpi.record_year,
    kpi.record_month,
    kpi.record_date,
    kpi.country_code,
    kpi.country_region,

    -- Aggregati da MV (già pre-calcolati per shop)
    SUM(kpi.headcount)              AS total_headcount,
    ROUND(SUM(kpi.total_fte), 2)    AS total_fte,

    -- Compensazione
    SUM(kpi.total_commission)       AS total_commission,
    SUM(kpi.total_quarterly_bonus)  AS total_quarterly_bonus,
    SUM(kpi.total_annual_bonus)     AS total_annual_bonus,
    SUM(kpi.total_extra_booster)    AS total_extra_booster,
    SUM(kpi.total_other_compensation) AS total_other_compensation,
    SUM(kpi.total_variable)         AS total_variable,

    -- Vendite
    SUM(kpi.total_sales)            AS total_sales,
    SUM(kpi.total_ha_sales)         AS total_ha_sales,
    SUM(kpi.total_monthly_target)   AS total_monthly_target,

    -- KPI calcolati a livello paese (ricalcolo dalla somma per correttezza)
    ROUND(
        SUM(kpi.total_sales) / NULLIF(SUM(kpi.total_variable), 0),
        4
    )                               AS country_compensation_efficiency,

    ROUND(
        SUM(kpi.total_variable) / NULLIF(SUM(kpi.total_sales), 0) * 100,
        2
    )                               AS country_cost_to_revenue_pct,

    ROUND(
        SUM(kpi.total_sales) / NULLIF(SUM(kpi.total_monthly_target), 0) * 100,
        2
    )                               AS country_target_achievement_pct,

    -- Numero shop attivi con dati nel periodo
    COUNT(DISTINCT kpi.shop_id)     AS shops_with_data

FROM rise_core.mv_kpi_monthly kpi

WHERE
    -- Filtro periodo: ultimi 12 mesi scorrevoli (modificare a piacere)
    kpi.record_date >= (date_trunc('month', now()) - INTERVAL '11 month')::DATE
    AND kpi.record_date <= (date_trunc('month', now()))::DATE

    -- Filtro paese opzionale (commentare per tutti)
    -- AND kpi.country_code = ANY(:country_codes)

GROUP BY
    kpi.record_year,
    kpi.record_month,
    kpi.record_date,
    kpi.country_code,
    kpi.country_region

ORDER BY
    kpi.country_code,
    kpi.record_date DESC;
```

---

### Q2 – Assegnazione Cluster con `pgp_sym_decrypt` GDPR-aware

```sql
-- =============================================================
-- Q2: Assegnazione cluster performance per dipendente
--     - Calcola l'achievement % mensile
--     - Assegna il cluster dalla tabella cluster_definitions
--     - Decripta i dati PII solo se l'utente ha has_gdpr_access
--
-- Parametri:
--   :session_user_id  – id dell'utente che esegue la query
--   :pgp_key          – chiave simmetrica (passata dalla app, MAI senza vault)
--   :p_year           – anno di riferimento
--   :p_month          – mese di riferimento
-- =============================================================

WITH

-- Recupero flag GDPR dell'utente corrente
session_user_info AS (
    SELECT has_gdpr_access
    FROM rise_core.users
    WHERE id = :session_user_id
      AND is_active = TRUE
      AND deleted_at IS NULL
    LIMIT 1
),

-- Performance mensile per dipendente
employee_performance AS (
    SELECT
        mpr.employee_id,
        mpr.shop_id,
        mpr.record_year,
        mpr.record_month,
        mpr.total_sales,
        mpr.monthly_target,
        -- Achievement %: vendite / target * 100
        ROUND(
            mpr.total_sales / NULLIF(mpr.monthly_target, 0) * 100,
            2
        )                               AS achievement_pct,
        mpr.commission,
        mpr.quarterly_bonus,
        mpr.annual_bonus,
        mpr.extra_booster,
        mpr.other_compensation,
        (mpr.commission + mpr.quarterly_bonus + mpr.annual_bonus
         + mpr.extra_booster + mpr.other_compensation) AS total_variable
    FROM rise_core.monthly_performance_records mpr
    WHERE mpr.record_year  = :p_year
      AND mpr.record_month = :p_month
      AND mpr.employee_id  IS NOT NULL
),

-- Assegnazione cluster (range aperto superiore per 'Top')
clustered AS (
    SELECT
        ep.*,
        cd.cluster_name,
        cd.color_hex,
        cd.display_order
    FROM employee_performance ep
    JOIN rise_core.cluster_definitions cd
      ON cd.is_active = TRUE
     AND ep.achievement_pct >= cd.min_pct
     AND (cd.max_pct IS NULL OR ep.achievement_pct <= cd.max_pct)
)

-- Output finale con decifrazione GDPR-aware
SELECT
    cl.employee_id,
    s.shop_code,
    c.code                          AS country_code,

    -- Decifrazione condizionale: PII visibili solo con has_gdpr_access = TRUE
    CASE
        WHEN (SELECT has_gdpr_access FROM session_user_info) = TRUE
        THEN convert_from(
                pgp_sym_decrypt(e.full_name_enc, :pgp_key),
                'UTF8'
             )
        ELSE '*** PROTECTED ***'
    END                             AS employee_name,

    CASE
        WHEN (SELECT has_gdpr_access FROM session_user_info) = TRUE
             AND e.email_enc IS NOT NULL
        THEN convert_from(
                pgp_sym_decrypt(e.email_enc, :pgp_key),
                'UTF8'
             )
        ELSE '*** PROTECTED ***'
    END                             AS employee_email,

    cl.record_year,
    cl.record_month,
    cl.total_sales,
    cl.monthly_target,
    cl.achievement_pct,
    cl.total_variable,
    cl.cluster_name,
    cl.color_hex,
    cl.display_order

FROM clustered cl
JOIN rise_core.employees e ON e.id = cl.employee_id
JOIN rise_core.shops      s ON s.id = cl.shop_id
JOIN rise_core.countries  c ON c.id = s.country_id

WHERE e.deleted_at IS NULL
  AND s.deleted_at IS NULL

ORDER BY
    c.code,
    s.shop_code,
    cl.display_order,
    cl.achievement_pct DESC;
```

---

### Q3 – Trend YoY con funzione `LAG()` Window

```sql
-- =============================================================
-- Q3: Trend Year-over-Year per paese con LAG()
--     Confronta ogni mese con lo stesso mese dell'anno precedente
--     Fonte: mv_kpi_monthly (evita aggregazione live)
-- =============================================================

WITH

-- Aggregazione mensile per paese (dalla MV già aggregata per shop)
country_monthly AS (
    SELECT
        kpi.country_code,
        kpi.country_region,
        kpi.record_year,
        kpi.record_month,
        kpi.record_date,
        SUM(kpi.total_sales)         AS total_sales,
        SUM(kpi.total_variable)      AS total_variable,
        SUM(kpi.headcount)           AS headcount,
        ROUND(SUM(kpi.total_fte), 2) AS total_fte,
        ROUND(
            SUM(kpi.total_sales) / NULLIF(SUM(kpi.total_monthly_target), 0) * 100,
            2
        )                            AS target_achievement_pct
    FROM rise_core.mv_kpi_monthly kpi
    GROUP BY
        kpi.country_code,
        kpi.country_region,
        kpi.record_year,
        kpi.record_month,
        kpi.record_date
),

-- Calcolo YoY tramite LAG() con offset 12 mesi (stesso mese anno prec.)
yoy AS (
    SELECT
        cm.*,

        -- Vendite anno precedente (lag di 12 posizioni per stesso mese)
        LAG(cm.total_sales, 12) OVER (
            PARTITION BY cm.country_code
            ORDER BY cm.record_year, cm.record_month
        )                                              AS prev_year_sales,

        -- Variabile anno precedente
        LAG(cm.total_variable, 12) OVER (
            PARTITION BY cm.country_code
            ORDER BY cm.record_year, cm.record_month
        )                                              AS prev_year_variable,

        -- Headcount anno precedente
        LAG(cm.headcount, 12) OVER (
            PARTITION BY cm.country_code
            ORDER BY cm.record_year, cm.record_month
        )                                              AS prev_year_headcount,

        -- Achievement anno precedente
        LAG(cm.target_achievement_pct, 12) OVER (
            PARTITION BY cm.country_code
            ORDER BY cm.record_year, cm.record_month
        )                                              AS prev_year_achievement_pct
    FROM country_monthly cm
)

SELECT
    y.country_code,
    y.country_region,
    y.record_year,
    y.record_month,
    y.record_date,

    y.total_sales,
    y.total_variable,
    y.headcount,
    y.target_achievement_pct,

    y.prev_year_sales,
    y.prev_year_variable,
    y.prev_year_headcount,
    y.prev_year_achievement_pct,

    -- Delta YoY assoluto
    (y.total_sales - y.prev_year_sales)                AS sales_yoy_delta,

    -- Variazione YoY percentuale
    ROUND(
        (y.total_sales - y.prev_year_sales)
        / NULLIF(y.prev_year_sales, 0) * 100,
        2
    )                                                  AS sales_yoy_pct_change,

    -- Variazione costo variabile YoY %
    ROUND(
        (y.total_variable - y.prev_year_variable)
        / NULLIF(y.prev_year_variable, 0) * 100,
        2
    )                                                  AS variable_yoy_pct_change,

    -- Delta achievement (pp = punti percentuali)
    ROUND(
        y.target_achievement_pct - y.prev_year_achievement_pct,
        2
    )                                                  AS achievement_pp_change

FROM yoy y

WHERE
    -- Mostriamo almeno 2 anni dati per avere il comparativo
    y.record_year >= EXTRACT(YEAR FROM now())::SMALLINT - 2

ORDER BY
    y.country_code,
    y.record_date DESC;
```

---

### Q4 – Export Completo con GDPR Masking Condizionale

```sql
-- =============================================================
-- Q4: Export completo dipendenti + KPI con GDPR masking
--     condizionale su has_gdpr_access dell'utente corrente
--
-- Parametri:
--   :session_user_id  – id utente che esegue l'export
--   :pgp_key          – chiave PGP passata dalla app
--   :p_year           – anno di riferimento
--   :p_month          – mese di riferimento
--   :p_country_codes  – array di codici paese (NULL = tutti)
-- =============================================================

WITH

-- Info utente sessione
session_context AS (
    SELECT
        u.id              AS user_id,
        u.role,
        u.has_gdpr_access,
        u.country_scope
    FROM rise_core.users u
    WHERE u.id = :session_user_id
      AND u.is_active = TRUE
      AND u.deleted_at IS NULL
    LIMIT 1
),

-- Paesi accessibili per l'utente
accessible_countries AS (
    SELECT c.id AS country_id, c.code AS country_code
    FROM rise_core.countries c
    WHERE
        -- GLOBAL_ADMIN vede tutto
        (SELECT role FROM session_context) = 'GLOBAL_ADMIN'
        -- COUNTRY_MANAGER vede solo i propri paesi (o filtro esplicito)
        OR c.code = ANY(
            COALESCE(
                :p_country_codes::TEXT[],
                (SELECT country_scope FROM session_context)
            )
        )
),

-- Dati performance del mese richiesto
perf AS (
    SELECT
        mpr.employee_id,
        mpr.shop_id,
        mpr.record_year,
        mpr.record_month,
        mpr.record_date,
        mpr.fte,
        mpr.commission,
        mpr.quarterly_bonus,
        mpr.annual_bonus,
        mpr.extra_booster,
        mpr.other_compensation,
        (mpr.commission + mpr.quarterly_bonus + mpr.annual_bonus
         + mpr.extra_booster + mpr.other_compensation) AS total_variable,
        mpr.total_sales,
        mpr.ha_sales,
        mpr.other_sales,
        mpr.monthly_target,
        ROUND(mpr.total_sales / NULLIF(mpr.monthly_target, 0) * 100, 2)
                                                         AS achievement_pct
    FROM rise_core.monthly_performance_records mpr
    WHERE mpr.record_year  = :p_year
      AND mpr.record_month = :p_month
      AND mpr.employee_id  IS NOT NULL
)

SELECT
    -- Colonne geografiche
    cou.code                          AS country_code,
    cou.name                          AS country_name,
    cou.currency_code,
    s.shop_code,
    s.name                            AS shop_name,
    s.city                            AS shop_city,

    -- Identificativi dipendente
    e.id                              AS employee_id,
    e.oracle_hcm_id,

    -- PII con masking GDPR dinamico
    CASE
        WHEN (SELECT has_gdpr_access FROM session_context) = TRUE
        THEN convert_from(pgp_sym_decrypt(e.full_name_enc, :pgp_key), 'UTF8')
        ELSE '*** PROTECTED ***'
    END                               AS full_name,

    CASE
        WHEN (SELECT has_gdpr_access FROM session_context) = TRUE
             AND e.email_enc IS NOT NULL
        THEN convert_from(pgp_sym_decrypt(e.email_enc, :pgp_key), 'UTF8')
        ELSE '*** PROTECTED ***'
    END                               AS email,

    e.hire_date,
    CASE
        WHEN (SELECT has_gdpr_access FROM session_context) = TRUE
        THEN e.termination_date::TEXT
        ELSE NULL
    END                               AS termination_date,

    -- Dati performance
    p.record_year,
    p.record_month,
    p.record_date,
    p.fte,
    p.commission,
    p.quarterly_bonus,
    p.annual_bonus,
    p.extra_booster,
    p.other_compensation,
    p.total_variable,
    p.total_sales,
    p.ha_sales,
    p.other_sales,
    p.monthly_target,
    p.achievement_pct,

    -- Cluster assegnato
    cd.cluster_name,
    cd.color_hex,

    -- Timestamp export (per traceability)
    now()                             AS exported_at,
    (SELECT user_id FROM session_context) AS exported_by_user_id

FROM perf p
JOIN rise_core.employees   e   ON e.id  = p.employee_id    AND e.deleted_at IS NULL
JOIN rise_core.shops        s   ON s.id  = p.shop_id        AND s.deleted_at IS NULL
JOIN rise_core.countries    cou ON cou.id = s.country_id
JOIN accessible_countries   ac  ON ac.country_id = cou.id
-- Cluster join (LEFT per non perdere record senza cluster configurato)
LEFT JOIN rise_core.cluster_definitions cd
       ON cd.is_active = TRUE
      AND p.achievement_pct >= cd.min_pct
      AND (cd.max_pct IS NULL OR p.achievement_pct <= cd.max_pct)

ORDER BY
    cou.code,
    s.shop_code,
    e.oracle_hcm_id;
```

---

## §7 Migrazioni Flyway

| Migrazione | Descrizione |
|------------|-------------|
| `V001__create_extensions.sql` | `CREATE EXTENSION pgcrypto` e `CREATE EXTENSION btree_gist`; verifica versioni minime PG15. |
| `V002__create_schemas_and_role.sql` | Creazione schemi `rise_core` e `rise_audit`; creazione ruolo `rise_app`; grant di `USAGE` sugli schemi. |
| `V003__create_countries.sql` | DDL tabella `rise_core.countries` con PK, UNIQUE su `code`, indici. |
| `V004__create_shops.sql` | DDL tabella `rise_core.shops` con FK su `countries`, indici per `country_id` e soft-delete. |
| `V005__create_employees.sql` | DDL tabella `rise_core.employees` con colonne BYTEA cifrate, FK su `shops`, vincoli FTE. |
| `V006__create_users.sql` | DDL tabella `rise_core.users` con CHECK su `role`, colonna array `country_scope`. |
| `V007__create_import_templates.sql` | DDL tabella `rise_core.import_templates` con JSONB columns, FK su `users`. |
| `V008__create_import_logs.sql` | DDL tabella `rise_core.import_logs` con tutti i CHECK su `status`/`data_type`/`duplicate_resolution`. |
| `V009__create_import_log_rows.sql` | DDL tabella `rise_core.import_log_rows` partizionata su `import_log_id`; creazione partizione default. |
| `V010__create_monthly_performance_records.sql` | DDL tabella `rise_core.monthly_performance_records` con colonna `GENERATED`, UNIQUE composito, tutti i FK e CHECK. |
| `V011__create_cluster_definitions.sql` | DDL `rise_core.cluster_definitions` + INSERT seed dei 5 cluster (Top, Medium High, Medium, Low, Worst). |
| `V012__create_cost_coefficients.sql` | DDL `rise_core.cost_coefficients` con vincolo `EXCLUDE USING GIST` per non-overlap temporale. |
| `V013__create_audit_events.sql` | DDL `rise_audit.audit_events`; funzione e trigger di immutabilità; `REVOKE UPDATE, DELETE FROM rise_app`. |
| `V014__create_mv_kpi_monthly.sql` | Creazione `mv_kpi_monthly` con `WITH DATA`; `UNIQUE INDEX` su `(record_year, record_month, shop_id)`; funzione `fn_refresh_mv_kpi_monthly`. |
| `V015__grant_rise_app_permissions.sql` | Grant finali di `SELECT/INSERT/UPDATE/DELETE` su tutte le tabelle `rise_core` a `rise_app`; `SELECT, INSERT` su `rise_audit`; `EXECUTE` su `fn_refresh_mv_kpi_monthly`; revoca su `audit_events` in conformità §3.12. |

---

## §8 Policy GDPR, Retention e Masking

| Dato | Storage | Cifratura at-rest | Comportamento Export | Retention | Azione Anonimizzazione |
|------|---------|-------------------|----------------------|-----------|------------------------|
| `employees.full_name_enc` | PostgreSQL BYTEA | `pgp_sym_encrypt` (chiave in vault esterno) | Visibile in chiaro solo se `has_gdpr_access = TRUE`; altrimenti `'*** PROTECTED ***'` | Conservato 10 anni post termine rapporto (obblighi giuslavoristici) | Dopo scadenza retention: `UPDATE` con sovrascrittura con `pgp_sym_encrypt('ANONIMIZZATO', key)`; `oracle_hcm_id` mantenuto per integrità RI |
| `employees.email_enc` | PostgreSQL BYTEA | `pgp_sym_encrypt` (chiave in vault esterno) | Solo con `has_gdpr_access = TRUE`; NULL restituito altrimenti | 7 anni post termine (GDPR art. 17 – bilanciamento con obblighi) | Sovrascrittura con NULL o valore mascherato; audit event registrato |
| `employees.termination_date` | DATE in chiaro | Crittografia trasparente TDE (a livello filesystem/tablespace) | Nascosta negli export senza `has_gdpr_access`; sostituita con NULL | 10 anni post data terminazione | Impostazione a NULL dopo scadenza retention |
| `users.email` | VARCHAR in chiaro | TDE a livello filesystem | Visibile solo a GLOBAL_ADMIN e SYSTEM_ADMIN | Finché il profilo utente è attivo + 5 anni post disattivazione | Sostituzione con email anonima `anon_{id}@rise.invalid` al soft-delete permanente |
| `import_log_rows.raw_data` | JSONB | TDE filesystem | Non esposta nelle API pubbliche; solo per debug SYSTEM_ADMIN | 2 anni dalla data di import | Cancellazione logica (SET raw_data = NULL) dopo 2 anni; la riga rimane per statistiche aggregabili |
| `audit_events.payload` | JSONB | TDE filesystem | Solo SYSTEM_ADMIN e GLOBAL_ADMIN | Permanente (immutabile per legge – art. 8 GDPR, e-discovery) | Non anonimizzabile per vincolo legale; accesso ristretto per data minimization |
| `audit_events.actor_email` | VARCHAR in chiaro | TDE filesystem | Solo SYSTEM_ADMIN | Permanente | Pseudonimizzazione post 7 anni: sostituzione con hash SHA-256 dell'email tramite `digest(actor_email, 'sha256')` |
| `monthly_performance_records` (dati finanziari) | NUMERIC in chiaro | TDE filesystem | Visibili in export; senza PII dirette | 10 anni (obblighi fiscali nazionali) | Aggregazione e rimozione del legame con `employee_id` (SET employee_id = NULL) dopo scadenza retentio |
| `cost_coefficients.value` | NUMERIC in chiaro | TDE filesystem | Visibile a utenti autenticati con accesso paese | Conservato finché vigente + 7 anni (obblighi contrattuali) | Nessuna anonimizzazione richiesta (dato non personale); archiviazione in tabella storica separata |

---

## DBA_AGENT_SIGNATURE_V2

```
╔══════════════════════════════════════════════════════════════╗
║          DBA AGENT – RISE SPENDING EFFECTIVENESS            ║
║                  DATA MODEL v2.0                            ║
╠══════════════════════════════════════════════════════════════╣
║  Generato da  :  DBA Agent (GitHub Copilot –               ║
║                  Claude Sonnet 4.6)                         ║
║  Data          :  2026-03-04                                ║
║  Database       :  PostgreSQL 15+                           ║
║  Schemi         :  rise_core, rise_audit                    ║
║  Tabelle        :  11                                       ║
║  MV             :  1 (mv_kpi_monthly)                       ║
║  Indici         :  28 (inclusi UNIQUE, PARTIAL, GIST)       ║
║  Migrazioni     :  V001 – V015                              ║
║  Estensioni     :  pgcrypto, btree_gist                     ║
╠══════════════════════════════════════════════════════════════╣
║  This section must be present and must not be removed.      ║
╚══════════════════════════════════════════════════════════════╝
```
