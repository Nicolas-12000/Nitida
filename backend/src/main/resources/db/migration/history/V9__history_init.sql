CREATE SCHEMA IF NOT EXISTS history;

-- Per-motorcycle head of the chain: the row that is locked while appending (SELECT ... FOR UPDATE).
CREATE TABLE history.chain_head (
    motorcycle_id uuid PRIMARY KEY,
    last_seq      bigint NOT NULL,
    last_hash     bytea NOT NULL,
    version       bigint NOT NULL DEFAULT 0
);

-- Append-only: the application never updates or deletes rows. public_content is stored exactly as hashed (JCS).
CREATE TABLE history.history_entry (
    id              uuid PRIMARY KEY,
    motorcycle_id   uuid NOT NULL,
    seq             bigint NOT NULL,
    type            varchar(30) NOT NULL,
    workshop_id     uuid NOT NULL,
    source          varchar(80) UNIQUE,
    public_content  varchar(65000) NOT NULL,
    private_content varchar(65000) NOT NULL,
    private_hash    varchar(64) NOT NULL,
    previous_hash   bytea NOT NULL,
    hash            bytea NOT NULL,
    signature       bytea NOT NULL,
    key_id          uuid NOT NULL,
    recorded_at     timestamp with time zone NOT NULL,
    CONSTRAINT ux_history_moto_seq UNIQUE (motorcycle_id, seq)
);
CREATE INDEX ix_history_entry_workshop ON history.history_entry (workshop_id, motorcycle_id);

CREATE TABLE history.public_link (
    id            uuid PRIMARY KEY,
    motorcycle_id uuid NOT NULL,
    workshop_id   uuid,
    created_by    uuid NOT NULL,
    token_hash    varchar(64) NOT NULL UNIQUE,
    created_at    timestamp with time zone NOT NULL,
    expires_at    timestamp with time zone NOT NULL,
    revoked_at    timestamp with time zone
);
CREATE INDEX ix_public_link_moto ON history.public_link (motorcycle_id);
