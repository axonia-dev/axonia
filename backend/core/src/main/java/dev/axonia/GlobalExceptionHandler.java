package dev.axonia;

import java.util.Comparator;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * {@code @Order(HIGHEST_PRECEDENCE + 1)} is load-bearing, not decorative. Spring Boot auto-configures its own unordered
 * {@code @ControllerAdvice} ({@code ProblemDetailsExceptionHandler}, extending {@code ResponseEntityExceptionHandler})
 * that already handles every standard Spring MVC exception, {@link MethodArgumentNotValidException} included. Two
 * unordered advice beans tie at {@link Ordered#LOWEST_PRECEDENCE}, and {@code ExceptionHandlerExceptionResolver} picks
 * whichever one it iterates to first for a matching type - which was Boot's, silently swallowing
 * {@link #handleFieldValidation} and producing its own bare, uncoded {@code ProblemDetail} instead. One step behind
 * {@link Ordered#HIGHEST_PRECEDENCE} rather than on it, so {@code ScimExceptionHandler}'s own
 * {@code @Order(HIGHEST_PRECEDENCE)} - scoped to {@code dev.axonia.identity.scim} - still wins there without relying on
 * a tie-break between two beans at the exact same order.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class GlobalExceptionHandler {

    private static final String FIELD_INVALID = "field_invalid";
    private static final String REQUIRES_OWN_SESSION_CODE = "REQUIRES_OWN_SESSION";

    @ExceptionHandler(MissingOrganizationException.class)
    ProblemDetail handleMissingOrganization(MissingOrganizationException ex) {

        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException ex) {

        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex) {

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("code", ex.getCode());
        return problem;
    }

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail handleForbidden(ForbiddenException ex) {

        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    ProblemDetail handleValidation(ValidationException ex) {

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setProperty("code", ex.getCode());
        return problem;
    }

    /**
     * One mapping for every guard that needs the person's own sign-in, so the frontend has one {@code code} to check
     * for, whichever guard refused the call.
     */
    @ExceptionHandler(OwnSignInRequiredException.class)
    ProblemDetail handleOwnSignInRequired(OwnSignInRequiredException ex) {

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setProperty("code", REQUIRES_OWN_SESSION_CODE);
        return problem;
    }

    /**
     * A {@code @Valid} request body failure carries no code of its own - the constraint lives on a field of the request
     * DTO, never on a domain exception - so this reports the same {@code code}/{@code detail} shape
     * {@link #handleValidation} does, with a stable {@code code} rather than one made from the field name, plus a
     * {@code field} property naming which one failed. When several fields fail at once, only the first is reported: the
     * same choice {@code ScimExceptionHandler} already makes, and this app's coded refusal is a single code and detail
     * everywhere else too, never a list. Hibernate Validator accumulates violations in a plain {@code HashSet}, so the
     * order {@link org.springframework.validation.BindingResult#getFieldErrors()} hands back is hash-bucket order, not
     * declaration order, and JSR-380 leaves it unspecified besides - so "the first" is sorted by field name here, the
     * simplest rule that makes it the same field every time regardless of hash order or JVM. A client fixes that field
     * and resubmits, and deterministically sees the next failure, if any, on the next round trip. {@code detail} states
     * the rule the field broke ({@code FieldError#getDefaultMessage}), never the value the caller sent: that value came
     * from an operator and may be destined for a public page, and echoing arbitrary input back into an error body is
     * how reflection bugs start.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleFieldValidation(MethodArgumentNotValidException ex) {

        var fieldError = ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField)).findFirst();
        var field = fieldError.map(FieldError::getField).orElse(null);
        var rule = fieldError.map(FieldError::getDefaultMessage).orElse("The request body failed validation.");

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                field == null ? rule : field + ": " + rule);
        problem.setProperty("code", FIELD_INVALID);
        if (field != null) {
            problem.setProperty("field", field);
        }
        return problem;
    }
}
