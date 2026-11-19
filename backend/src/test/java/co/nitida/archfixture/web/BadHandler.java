package co.nitida.archfixture.web;

import org.springframework.jdbc.core.simple.JdbcClient;

public class BadHandler {
    private final JdbcClient jdbc;

    public BadHandler(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Object anything() {
        return jdbc;
    }
}
