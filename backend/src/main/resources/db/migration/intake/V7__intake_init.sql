CREATE SCHEMA IF NOT EXISTS intake;

CREATE TABLE intake.intake (
    id                  uuid PRIMARY KEY,
    workshop_id         uuid NOT NULL,
    motorcycle_id       uuid NOT NULL,
    mileage             int NOT NULL,
    odometer_photo_id   uuid NOT NULL,
    motorcycle_photo_id uuid NOT NULL,
    received_by         uuid NOT NULL,
    status              varchar(20) NOT NULL,
    discard_reason      varchar(30),
    work_order_id       uuid,
    created_at          timestamp with time zone NOT NULL,
    version             bigint NOT NULL DEFAULT 0
);
CREATE INDEX ix_intake_workshop_status ON intake.intake (workshop_id, status, created_at);
CREATE INDEX ix_intake_motorcycle ON intake.intake (motorcycle_id);
