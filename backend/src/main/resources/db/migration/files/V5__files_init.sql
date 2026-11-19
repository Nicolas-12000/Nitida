CREATE SCHEMA IF NOT EXISTS files;

CREATE TABLE files.stored_file (
    id          uuid PRIMARY KEY,
    workshop_id uuid NOT NULL,
    mime_type   varchar(60) NOT NULL,
    size_bytes  bigint NOT NULL,
    sha256      varchar(64) NOT NULL,
    path        varchar(255) NOT NULL,
    created_at  timestamp with time zone NOT NULL
);
CREATE INDEX ix_stored_file_workshop ON files.stored_file (workshop_id);
