#!/bin/sh
# Runs once, when the postgres data directory is created (docker-entrypoint-initdb.d).
# Creates the role the APPLICATION connects as: it owns no tables, is not a superuser and has no BYPASSRLS, so the
# row-level security policies of V15__row_level_security.sql always apply to it. The owner role (POSTGRES_USER)
# is only for Flyway, which creates and changes the schema.
set -e

: "${NITIDA_APP_DB_PASSWORD:?set NITIDA_APP_DB_PASSWORD in .env}"

psql -v ON_ERROR_STOP=1 -v app_password="$NITIDA_APP_DB_PASSWORD" --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
CREATE ROLE nitida_app LOGIN PASSWORD :'app_password' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
SQL
