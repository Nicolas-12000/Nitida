package co.nitida.shared.web;

import co.nitida.shared.ApiException;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Every error leaves as RFC 9457 Problem Details with a stable code; never stack traces (A10). */
@RestControllerAdvice
class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> api(ApiException e) {
        return problem(e.status(), e.code(), e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    ResponseEntity<ProblemDetail> unreadable(Exception e) {
        log.debug("bad request", e);
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "La solicitud no es válida");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> version(OptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Otra persona modificó este registro, recargue e intente de nuevo");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> integrity(DataIntegrityViolationException e) {
        log.warn("data integrity violation", e);
        return problem(HttpStatus.CONFLICT, "CONFLICT", "La operación choca con un dato existente");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> denied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "No tiene permiso para esta acción");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> noResource(NoResourceFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "No encontramos lo que busca");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e) {
        String traceId = UUID.randomUUID().toString();
        log.error("unexpected error traceId={}", traceId, e);
        var response = problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Ocurrió un error inesperado");
        response.getBody().setProperty("traceId", traceId);
        return response;
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String title) {
        var pd = ProblemDetail.forStatus(status);
        pd.setType(URI.create("https://nitida.co/problems/" + code.toLowerCase().replace('_', '-')));
        pd.setTitle(title);
        pd.setProperty("code", code);
        pd.setProperty("traceId", UUID.randomUUID().toString());
        return ResponseEntity.status(status).body(pd);
    }
}
