CREATE SCHEMA IF NOT EXISTS identity;

CREATE TABLE identity.app_user (
    id              uuid PRIMARY KEY,
    email           varchar(254) NOT NULL UNIQUE,
    password_hash   varchar(255) NOT NULL,
    display_name    varchar(120) NOT NULL,
    platform_admin  boolean NOT NULL DEFAULT false,
    created_at      timestamp with time zone NOT NULL,
    locked_until    timestamp with time zone,
    failed_attempts int NOT NULL DEFAULT 0
);

CREATE TABLE identity.membership (
    user_id     uuid NOT NULL REFERENCES identity.app_user (id),
    workshop_id uuid NOT NULL,
    role        varchar(30) NOT NULL,
    active      boolean NOT NULL DEFAULT true,
    PRIMARY KEY (user_id, workshop_id, role)
);
CREATE INDEX ix_membership_workshop ON identity.membership (workshop_id);

CREATE TABLE identity.invitation (
    id          uuid PRIMARY KEY,
    workshop_id uuid NOT NULL,
    email       varchar(254) NOT NULL,
    role        varchar(30) NOT NULL,
    token_hash  varchar(64) NOT NULL UNIQUE,
    created_by  uuid NOT NULL,
    created_at  timestamp with time zone NOT NULL,
    expires_at  timestamp with time zone NOT NULL,
    used_at     timestamp with time zone
);
CREATE INDEX ix_invitation_workshop ON identity.invitation (workshop_id);
