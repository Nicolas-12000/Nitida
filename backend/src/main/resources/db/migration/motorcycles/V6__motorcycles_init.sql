CREATE SCHEMA IF NOT EXISTS motorcycles;

-- Motorcycles are global (not tenant data) and hold no personal data (RN-22).
CREATE TABLE motorcycles.motorcycle (
    id                  uuid PRIMARY KEY,
    vin                 varchar(17) UNIQUE,
    current_plate       varchar(6) UNIQUE,
    legacy_plate_format boolean NOT NULL DEFAULT false,
    brand               varchar(40) NOT NULL,
    line                varchar(40) NOT NULL,
    model_year          int,
    displacement_cc     int,
    created_at          timestamp with time zone NOT NULL
);

CREATE TABLE motorcycles.plate_history (
    id            uuid PRIMARY KEY,
    motorcycle_id uuid NOT NULL REFERENCES motorcycles.motorcycle (id),
    plate         varchar(6) NOT NULL,
    valid_from    timestamp with time zone NOT NULL,
    valid_to      timestamp with time zone
);
CREATE INDEX ix_plate_history_moto ON motorcycles.plate_history (motorcycle_id);

CREATE TABLE motorcycles.customer (
    id          uuid PRIMARY KEY,
    workshop_id uuid NOT NULL,
    full_name   varchar(120) NOT NULL,
    phone       varchar(20) NOT NULL,
    national_id varchar(15),
    email       varchar(254),
    created_at  timestamp with time zone NOT NULL,
    erased_at   timestamp with time zone
);
CREATE INDEX ix_customer_workshop ON motorcycles.customer (workshop_id);

CREATE TABLE motorcycles.data_consent (
    id             uuid PRIMARY KEY,
    workshop_id    uuid NOT NULL,
    customer_id    uuid NOT NULL REFERENCES motorcycles.customer (id),
    policy_version varchar(20) NOT NULL,
    channel        varchar(30) NOT NULL,
    recorded_by    uuid NOT NULL,
    recorded_at    timestamp with time zone NOT NULL
);
CREATE INDEX ix_data_consent_customer ON motorcycles.data_consent (customer_id);
