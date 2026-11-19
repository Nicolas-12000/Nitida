-- Row-level security (spec section 8.2, second barrier) and least-privilege grants for the application role.
-- PostgreSQL only: it lives in db/postgres, which the H2 test profile does not load.
--
-- Two roles, never the same one:
--   * the OWNER (the one Flyway connects as) owns the tables and is the only one that can change them;
--   * nitida_app (what the application connects as) owns nothing, is not a superuser and has no BYPASSRLS,
--     so the policies below always apply to it.
-- The transaction tells the database which workshop it acts for with set_config('app.workshop_id', ..., true)
-- (see TenantSession). With no workshop selected the application sees no row of these tables at all.

-- The role normally exists already (docker/postgres-init creates it with its login and password).
-- Created NOLOGIN here only so this migration can never fail on a database where it was not provisioned.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'nitida_app') THEN
        CREATE ROLE nitida_app NOLOGIN;
    END IF;
END
$$;

-- ---------------------------------------------------------------------------------------------------------------
-- Privileges: exactly what the application needs, and no DDL.
-- ---------------------------------------------------------------------------------------------------------------
GRANT USAGE ON SCHEMA public, identity, workshops, motorcycles, intake, files, orders, catalog, history, cashdesk, audit TO nitida_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public, identity, workshops, motorcycles, intake, files, orders, catalog, history, cashdesk, audit TO nitida_app;

-- Append-only by privilege, not just by convention (spec sections 5.7 and 13.2, A08/A09): the audit trail and the signed
-- history entries can be read and added to, never rewritten or erased by the application. A new entry's seq and hash are
-- fixed when it is written; corrections are new AMENDMENT entries.
REVOKE UPDATE, DELETE ON audit.audit_event FROM nitida_app;
REVOKE UPDATE, DELETE ON history.history_entry FROM nitida_app;

-- Tables created by later migrations (run by this same owner) get the same grants.
ALTER DEFAULT PRIVILEGES IN SCHEMA public, identity, workshops, motorcycles, intake, files, orders, catalog, history, cashdesk, audit
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO nitida_app;

-- ---------------------------------------------------------------------------------------------------------------
-- Policies: one per table that carries a workshop_id and belongs to one workshop.
--   own rows   : workshop_id equals the workshop selected for the transaction
--   system jobs: app.system = 'on', set only by the alert scheduler (it must look at every workshop)
-- USING also acts as WITH CHECK, so a workshop cannot insert or move rows into another one either.
-- NULLIF: after a transaction-local set_config the setting reads '' (not NULL) on a reused connection.
-- ---------------------------------------------------------------------------------------------------------------
ALTER TABLE orders.work_order ENABLE ROW LEVEL SECURITY;
CREATE POLICY workshop_isolation ON orders.work_order
    USING (workshop_id = NULLIF(current_setting('app.workshop_id', true), '')::uuid OR current_setting('app.system', true) = 'on');

ALTER TABLE orders.payment ENABLE ROW LEVEL SECURITY;
CREATE POLICY workshop_isolation ON orders.payment
    USING (workshop_id = NULLIF(current_setting('app.workshop_id', true), '')::uuid OR current_setting('app.system', true) = 'on');

ALTER TABLE intake.intake ENABLE ROW LEVEL SECURITY;
CREATE POLICY workshop_isolation ON intake.intake
    USING (workshop_id = NULLIF(current_setting('app.workshop_id', true), '')::uuid OR current_setting('app.system', true) = 'on');

ALTER TABLE motorcycles.customer ENABLE ROW LEVEL SECURITY;
CREATE POLICY workshop_isolation ON motorcycles.customer
    USING (workshop_id = NULLIF(current_setting('app.workshop_id', true), '')::uuid OR current_setting('app.system', true) = 'on');

ALTER TABLE motorcycles.data_consent ENABLE ROW LEVEL SECURITY;
CREATE POLICY workshop_isolation ON motorcycles.data_consent
    USING (workshop_id = NULLIF(current_setting('app.workshop_id', true), '')::uuid OR current_setting('app.system', true) = 'on');

ALTER TABLE catalog.product ENABLE ROW LEVEL SECURITY;
CREATE POLICY workshop_isolation ON catalog.product
    USING (workshop_id = NULLIF(current_setting('app.workshop_id', true), '')::uuid OR current_setting('app.system', true) = 'on');

ALTER TABLE cashdesk.cash_count ENABLE ROW LEVEL SECURITY;
CREATE POLICY workshop_isolation ON cashdesk.cash_count
    USING (workshop_id = NULLIF(current_setting('app.workshop_id', true), '')::uuid OR current_setting('app.system', true) = 'on');

ALTER TABLE cashdesk.leak_alert ENABLE ROW LEVEL SECURITY;
CREATE POLICY workshop_isolation ON cashdesk.leak_alert
    USING (workshop_id = NULLIF(current_setting('app.workshop_id', true), '')::uuid OR current_setting('app.system', true) = 'on');

ALTER TABLE files.stored_file ENABLE ROW LEVEL SECURITY;
CREATE POLICY workshop_isolation ON files.stored_file
    USING (workshop_id = NULLIF(current_setting('app.workshop_id', true), '')::uuid OR current_setting('app.system', true) = 'on');

-- Deliberately WITHOUT row-level security (they carry a workshop_id but are read before the workshop is known, are
-- global by design, or are written from every context; none holds personal data of customers):
--   identity.membership, identity.invitation   decide who may enter a workshop, so they are read first
--   workshops.signing_key                      signing runs from async listeners with no request
--   history.history_entry, history.public_link, catalog.serial   global, access controlled in the application (RN-22, RN-94)
--   audit.audit_event                          insert-only trail
-- orders.job, orders.part and orders.work_order_photo have no workshop_id: they are reached only through their order.
