package co.nitida.shared.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;

/** Writes a Problem Details body from places outside MVC (security filters). */
public final class ProblemResponses {
    private ProblemResponses() {}

    public static void write(HttpServletResponse response, HttpStatus status, String code, String title) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title.replace("\"", "'")
                + "\",\"status\":" + status.value() + ",\"code\":\"" + code + "\"}");
    }
}
