-- Spring Modulith event publication registry (retries for @ApplicationModuleListener).
CREATE TABLE event_publication (
    id                     uuid PRIMARY KEY,
    listener_id            varchar(512) NOT NULL,
    event_type             varchar(512) NOT NULL,
    serialized_event       varchar(65000) NOT NULL,
    publication_date       timestamp with time zone NOT NULL,
    completion_date        timestamp with time zone,
    status                 varchar(16),
    completion_attempts    int,
    last_resubmission_date timestamp with time zone
);
CREATE INDEX ix_event_publication_incomplete ON event_publication (completion_date, publication_date);
