CREATE TABLE IF NOT EXISTS rise_core.business_units (
    id SERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    country_code CHAR(2) NOT NULL,
    region VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS rise_core.user_business_units (
    user_id INT NOT NULL,
    business_unit_id INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, business_unit_id),
    CONSTRAINT fk_user_business_units_user
        FOREIGN KEY (user_id)
            REFERENCES rise_core.users(id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_user_business_units_business_unit
        FOREIGN KEY (business_unit_id)
            REFERENCES rise_core.business_units(id)
            ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_user_business_units_user_id
    ON rise_core.user_business_units(user_id);

CREATE INDEX IF NOT EXISTS idx_user_business_units_business_unit_id
    ON rise_core.user_business_units(business_unit_id);