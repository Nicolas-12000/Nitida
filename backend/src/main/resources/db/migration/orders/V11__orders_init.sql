CREATE SCHEMA IF NOT EXISTS orders;

CREATE TABLE orders.work_order (
    id                    uuid PRIMARY KEY,
    workshop_id           uuid NOT NULL,
    intake_id             uuid NOT NULL UNIQUE,
    motorcycle_id         uuid NOT NULL,
    customer_id           uuid,
    status                varchar(20) NOT NULL,
    diagnosis             varchar(2000) NOT NULL,
    intake_mileage        int NOT NULL,
    labor_cost            bigint NOT NULL DEFAULT 0,
    warranty_days         int,
    warranty_km           int,
    is_warranty           boolean NOT NULL DEFAULT false,
    warranty_ref_order_id uuid,
    mechanic_id           uuid,
    cancel_reason         varchar(500),
    reopen_reason         varchar(500),
    claim_code_hash       varchar(64),
    delivered_at          timestamp with time zone,
    created_at            timestamp with time zone NOT NULL,
    updated_at            timestamp with time zone NOT NULL,
    version               bigint NOT NULL DEFAULT 0
);
CREATE INDEX ix_work_order_workshop_status ON orders.work_order (workshop_id, status);
CREATE INDEX ix_work_order_stalled ON orders.work_order (status, updated_at);

CREATE TABLE orders.job (
    id            uuid PRIMARY KEY,
    work_order_id uuid NOT NULL REFERENCES orders.work_order (id),
    type          varchar(30) NOT NULL,
    description   varchar(500) NOT NULL
);
CREATE INDEX ix_job_order ON orders.job (work_order_id);

CREATE TABLE orders.part (
    id                    uuid PRIMARY KEY,
    work_order_id         uuid NOT NULL REFERENCES orders.work_order (id),
    name                  varchar(120) NOT NULL,
    reference             varchar(60),
    brand                 varchar(60) NOT NULL,
    condition             varchar(20) NOT NULL,
    quantity              int NOT NULL,
    unit_price            bigint NOT NULL,
    product_code          varchar(14),
    code_type             varchar(10),
    serial_id             uuid,
    serial_normalized     varchar(64),
    evidence_level        varchar(30) NOT NULL,
    authenticity_photo_id uuid,
    authenticity_method   varchar(200),
    replacement_photo_id  uuid
);
CREATE INDEX ix_part_order ON orders.part (work_order_id);

CREATE TABLE orders.work_order_photo (
    id            uuid PRIMARY KEY,
    work_order_id uuid NOT NULL REFERENCES orders.work_order (id),
    file_id       uuid NOT NULL,
    moment        varchar(10) NOT NULL
);
CREATE INDEX ix_order_photo_order ON orders.work_order_photo (work_order_id);

CREATE TABLE orders.payment (
    id            uuid PRIMARY KEY,
    workshop_id   uuid NOT NULL,
    work_order_id uuid NOT NULL REFERENCES orders.work_order (id),
    method        varchar(20) NOT NULL,
    amount        bigint NOT NULL,
    recorded_by   uuid NOT NULL,
    recorded_at   timestamp with time zone NOT NULL,
    voided_at     timestamp with time zone,
    void_reason   varchar(500)
);
CREATE INDEX ix_payment_order ON orders.payment (work_order_id);
CREATE INDEX ix_payment_cash_day ON orders.payment (workshop_id, method, recorded_at);
