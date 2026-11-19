-- TOTP (spec section 10). The secret is AES-GCM sealed with the KEK, never stored in clear.
-- totp_last_step is the last accepted 30-second step: a code can be used only once.
ALTER TABLE identity.app_user ADD COLUMN totp_secret bytea;
ALTER TABLE identity.app_user ADD COLUMN totp_nonce bytea;
ALTER TABLE identity.app_user ADD COLUMN totp_enabled boolean NOT NULL DEFAULT false;
ALTER TABLE identity.app_user ADD COLUMN totp_last_step bigint NOT NULL DEFAULT 0;
