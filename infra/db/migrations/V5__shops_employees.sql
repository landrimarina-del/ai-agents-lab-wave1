CREATE TABLE IF NOT EXISTS rise_core.shops (
    id SERIAL PRIMARY KEY,
    shop_code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    country_code CHAR(2) NOT NULL,
    region VARCHAR(100) NOT NULL,
    business_unit_id INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_shops_business_unit
        FOREIGN KEY (business_unit_id)
            REFERENCES rise_core.business_units(id)
            ON DELETE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_shops_shop_code_ci
    ON rise_core.shops (LOWER(shop_code));

CREATE INDEX IF NOT EXISTS idx_shops_business_unit_id
    ON rise_core.shops (business_unit_id)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS rise_core.employees (
    id SERIAL PRIMARY KEY,
    employee_id VARCHAR(100) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    shop_id INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_employees_shop
        FOREIGN KEY (shop_id)
            REFERENCES rise_core.shops(id)
            ON DELETE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_employees_employee_id_ci
    ON rise_core.employees (LOWER(employee_id));

CREATE INDEX IF NOT EXISTS idx_employees_shop_id
    ON rise_core.employees (shop_id)
    WHERE deleted_at IS NULL;
