CREATE SCHEMA IF NOT EXISTS cashdesk;

CREATE TABLE cashdesk.cash_count (
    id            uuid PRIMARY KEY,
    workshop_id   uuid NOT NULL,
    count_day     date NOT NULL,
    expected      bigint NOT NULL,
    counted       bigint,
    difference    bigint,
    status        varchar(20) NOT NULL,
    closed_by     uuid,
    closed_at     timestamp with time zone,
    reopen_reason varchar(500),
    version       bigint NOT NULL DEFAULT 0,
    CONSTRAINT ux_cash_count_day UNIQUE (workshop_id, count_day)
);

-- Alerts are never deleted (RN-53). dedupe_key makes every producer idempotent (scheduler, retried listeners).
CREATE TABLE cashdesk.leak_alert (
    id           uuid PRIMARY KEY,
    workshop_id  uuid NOT NULL,
    type         varchar(40) NOT NULL,
    reference_id uuid,
    dedupe_key   varchar(160) NOT NULL,
    details      varchar(2000) NOT NULL,
    created_at   timestamp with time zone NOT NULL,
    resolved_at  timestamp with time zone,
    resolved_by  uuid,
    comment      varchar(500),
    CONSTRAINT ux_leak_alert_dedupe UNIQUE (workshop_id, dedupe_key)
);
CREATE INDEX ix_leak_alert_workshop ON cashdesk.leak_alert (workshop_id, resolved_at, created_at);
