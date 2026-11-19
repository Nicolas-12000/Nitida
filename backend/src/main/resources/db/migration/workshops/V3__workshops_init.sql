CREATE SCHEMA IF NOT EXISTS workshops;

CREATE TABLE workshops.workshop (
    id                 uuid PRIMARY KEY,
    name               varchar(120) NOT NULL,
    document_number    varchar(30) NOT NULL,
    address            varchar(200) NOT NULL,
    municipality       varchar(80) NOT NULL,
    phone              varchar(20) NOT NULL,
    facade_photo_id    uuid,
    status             varchar(30) NOT NULL,
    verified_at        timestamp with time zone,
    verified_by        uuid,
    suspended_at       timestamp with time zone,
    intake_alert_hours int NOT NULL DEFAULT 24,
    cash_tolerance     bigint NOT NULL DEFAULT 0,
    stalled_days       int NOT NULL DEFAULT 7,
    created_at         timestamp with time zone NOT NULL,
    version            bigint NOT NULL DEFAULT 0
);

CREATE TABLE workshops.signing_key (
    id                    uuid PRIMARY KEY,
    workshop_id           uuid NOT NULL REFERENCES workshops.workshop (id),
    algorithm             varchar(20) NOT NULL,
    public_key            bytea NOT NULL,
    encrypted_private_key bytea NOT NULL,
    nonce                 bytea NOT NULL,
    kek_version           int NOT NULL,
    created_at            timestamp with time zone NOT NULL,
    retired_at            timestamp with time zone
);
CREATE INDEX ix_signing_key_workshop ON workshops.signing_key (workshop_id);
