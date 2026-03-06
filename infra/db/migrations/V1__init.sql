CREATE SCHEMA IF NOT EXISTS rise_core;
CREATE SCHEMA IF NOT EXISTS rise_audit;

CREATE TABLE IF NOT EXISTS rise_core.countries (
    id SERIAL PRIMARY KEY,
    code CHAR(2) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS rise_audit.audit_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    payload JSONB,
    event_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);