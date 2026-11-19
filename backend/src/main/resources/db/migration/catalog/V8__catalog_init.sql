CREATE SCHEMA IF NOT EXISTS catalog;

-- MVP: catalog per workshop (RN-93). Prices never live here.
CREATE TABLE catalog.product (
    id          uuid PRIMARY KEY,
    workshop_id uuid NOT NULL,
    code        varchar(14) NOT NULL,
    code_type   varchar(10) NOT NULL,
    name        varchar(120) NOT NULL,
    brand       varchar(60) NOT NULL,
    reference   varchar(60),
    status      varchar(20) NOT NULL,
    created_at  timestamp with time zone NOT NULL,
    CONSTRAINT ux_product_workshop_code UNIQUE (workshop_id, code)
);

-- Global registry of unit serials (RN-94). 'active_serial' holds the serial only while ACTIVE, so the unique
-- constraint is the last barrier against one serial being active on two motorcycles (portable partial index).
CREATE TABLE catalog.serial (
    id                uuid PRIMARY KEY,
    normalized_serial varchar(64) NOT NULL,
    serial_hash       varchar(64) NOT NULL,
    motorcycle_id     uuid NOT NULL,
    workshop_id       uuid NOT NULL,
    work_order_id     uuid,
    status            varchar(10) NOT NULL,
    active_serial     varchar(64),
    registered_at     timestamp with time zone NOT NULL,
    retired_at        timestamp with time zone,
    reason            varchar(200),
    CONSTRAINT ux_serial_active UNIQUE (active_serial)
);
CREATE INDEX ix_serial_normalized ON catalog.serial (normalized_serial);
CREATE INDEX ix_serial_order ON catalog.serial (work_order_id);
