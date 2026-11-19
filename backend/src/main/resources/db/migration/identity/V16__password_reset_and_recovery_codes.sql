CREATE TABLE identity.password_reset (
    id          uuid PRIMARY KEY,
    user_id     uuid NOT NULL REFERENCES identity.app_user (id),
    token_hash  varchar(64) NOT NULL UNIQUE,
    created_at  timestamp with time zone NOT NULL,
    expires_at  timestamp with time zone NOT NULL,
    used_at     timestamp with time zone
);

CREATE TABLE identity.totp_recovery_code (
    id          uuid PRIMARY KEY,
    user_id     uuid NOT NULL REFERENCES identity.app_user (id),
    code_hash   varchar(64) NOT NULL,
    used_at     timestamp with time zone
);
CREATE INDEX ix_totp_recovery_user ON identity.totp_recovery_code (user_id);
