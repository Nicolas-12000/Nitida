CREATE SCHEMA IF NOT EXISTS audit;

-- Insert-only trail: the application never updates or deletes rows here.
CREATE TABLE audit.audit_event (
    id          uuid PRIMARY KEY,
    workshop_id uuid,
    user_id     uuid,
    action      varchar(80) NOT NULL,
    resource    varchar(80) NOT NULL,
    resource_id uuid,
    ip          varchar(64),
    created_at  timestamp with time zone NOT NULL,
    details     varchar(4000) NOT NULL
);
CREATE INDEX ix_audit_event_workshop ON audit.audit_event (workshop_id, created_at);

-- Spring Session JDBC (generic schema); managed here instead of auto-initialisation.
CREATE TABLE spring_session (
    primary_id            char(36) NOT NULL,
    session_id            char(36) NOT NULL,
    creation_time         bigint NOT NULL,
    last_access_time      bigint NOT NULL,
    max_inactive_interval int NOT NULL,
    expiry_time           bigint NOT NULL,
    principal_name        varchar(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);
CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id char(36) NOT NULL,
    attribute_name     varchar(200) NOT NULL,
    attribute_bytes    bytea NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);
