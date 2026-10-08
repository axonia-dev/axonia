package dev.axonia.identity.scim;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * SCIM-scoped exception handler that emits the SCIM error schema (RFC 7644 §3.12) with
 * {@code Content-Type: application/scim+json}.
 *
 * <p>
 * Scoped to the {@code dev.axonia.identity.scim} package so that SCIM controllers receive SCIM-shaped errors while the
 * app's {@link dev.axonia.GlobalExceptionHandler} keeps serving {@code /axonia/**} with RFC 7807 {@code ProblemDetail}.
 * {@link Order#HIGHEST_PRECEDENCE} ensures this advice wins over the unscoped global handler for any controller in this
 * package.
 */
@RestControllerAdvice(basePackages = "dev.axonia.identity.scim")
@Order(Ordered.HIGHEST_PRECEDENCE)
class ScimExceptionHandler {

    private static final MediaType SCIM_JSON = MediaType.valueOf("application/scim+json");

    @ExceptionHandler(ScimException.class)
    ResponseEntity<ScimError> handleScimException(ScimException ex) {

        HttpStatus status = ex.getStatus();
        return ResponseEntity.status(status).contentType(SCIM_JSON)
                .body(ScimError.of(status, ex.getScimType(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ScimError> handleValidation(MethodArgumentNotValidException ex) {

        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage()).findFirst().orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(SCIM_JSON)
                .body(ScimError.of(HttpStatus.BAD_REQUEST, detail));
    }
}
