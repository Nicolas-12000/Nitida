-- Same shape as docker/postgres-init: the application connects as a plain role that owns nothing.
CREATE ROLE nitida_app LOGIN PASSWORD 'app-test-password';
