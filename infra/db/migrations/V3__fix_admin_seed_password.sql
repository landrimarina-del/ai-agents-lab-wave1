-- Ensure pgcrypto is available for bcrypt-compatible hash generation
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Ensure admin seed exists and credentials are aligned for QA smoke
INSERT INTO rise_core.users (
    email,
    full_name,
    password_hash,
    role,
    country_scope,
    is_active,
    failed_attempts,
    locked_until,
    created_at,
    updated_at
)
SELECT
    'admin@rise.local',
    'Dev Admin',
    crypt('Admin123!', gen_salt('bf', 10)),
    'GLOBAL_ADMIN',
    '{}'::text[],
    TRUE,
    0,
    NULL,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM rise_core.users
    WHERE email = 'admin@rise.local'
);

-- Reset eventual lockout and force known password for existing admin
UPDATE rise_core.users
SET
    full_name = 'Dev Admin',
    password_hash = crypt('Admin123!', gen_salt('bf', 10)),
    role = 'GLOBAL_ADMIN',
    country_scope = '{}'::text[],
    is_active = TRUE,
    failed_attempts = 0,
    locked_until = NULL,
    updated_at = NOW()
WHERE email = 'admin@rise.local';
