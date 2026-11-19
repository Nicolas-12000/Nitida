-- Phase 2 (partial MVP): owner portal. One active owner per motorcycle (RN-81).
CREATE TABLE motorcycles.vehicle_owner (
    id            uuid PRIMARY KEY,
    motorcycle_id uuid NOT NULL REFERENCES motorcycles.motorcycle (id),
    user_id       uuid NOT NULL,
    valid_from    timestamp with time zone NOT NULL,
    valid_to      timestamp with time zone,
    -- holds the motorcycle id while the ownership is open, so a second open owner violates the unique constraint
    active_motorcycle uuid UNIQUE
);
CREATE INDEX ix_vehicle_owner_user ON motorcycles.vehicle_owner (user_id);

CREATE TABLE motorcycles.claim_code (
    id            uuid PRIMARY KEY,
    motorcycle_id uuid NOT NULL REFERENCES motorcycles.motorcycle (id),
    kind          varchar(20) NOT NULL,
    code_hash     varchar(64) NOT NULL UNIQUE,
    created_by    uuid,
    expires_at    timestamp with time zone NOT NULL,
    used_at       timestamp with time zone
);
CREATE INDEX ix_claim_code_moto ON motorcycles.claim_code (motorcycle_id);
