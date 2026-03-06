CREATE TABLE IF NOT EXISTS rise_core.users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    full_name VARCHAR(255),
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL CHECK (role IN ('GLOBAL_ADMIN', 'COUNTRY_MANAGER', 'SYSTEM_ADMIN')),
    country_scope TEXT[] NOT NULL DEFAULT '{}',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO rise_core.users (email, full_name, password_hash, role, country_scope, is_active)
SELECT
    'admin@rise.local',
    'Dev Admin',
    '$2a$10$hG7fQw6f4B8Q9N5Q4kQzV.5M5k3sCw6KXwD63Q5Q3V3J6r5TjHzyK',
    'GLOBAL_ADMIN',
    '{}'::text[],
    TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM rise_core.users
    WHERE email = 'admin@rise.local'
);
