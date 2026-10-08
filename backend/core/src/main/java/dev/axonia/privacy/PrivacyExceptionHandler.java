package dev.axonia.privacy;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Scoped controller advice for this module's two controllers, mapping {@link AccountErasureIncompleteException} to HTTP
 * 502 and an export rate-limit refusal to 429.
 *
 * <p>
 * A delegated session's refusal is not mapped here: {@link dev.axonia.OwnSignInRequiredException} is a shared-kernel
 * type, thrown by {@link PrivacyOwnSignInGuard} for this module and by other guards for other surfaces, so
 * {@link dev.axonia.GlobalExceptionHandler} maps it once for every caller rather than this advice mapping it a second
 * time for just this module's two controllers.
 *
 * <p>
 * Kept within the {@code privacy} module so that the root {@link dev.axonia.GlobalExceptionHandler} does not need to
 * reference privacy-specific types - preserving the Spring Modulith module boundary.
 *
 * <p>
 * 502, not 500, for an incomplete erasure: by the time that exception exists, every Organization's data has already
 * been erased. The one thing that failed is the identity-provider deletion - a downstream call this backend is a
 * gateway to - and it is safe to retry. A bare 500 would tell the caller the erasure failed and to try again from
 * scratch, which is exactly backwards from what actually happened.
 */
@RestControllerAdvice(assignableTypes = {PrivacyController.class, OrganizationExportController.class})
class PrivacyExceptionHandler {

    private static final String INCOMPLETE_CODE = "ACCOUNT_ERASURE_INCOMPLETE";
    private static final String RATE_LIMITED_CODE = "EXPORT_RATE_LIMITED";

    @ExceptionHandler(AccountErasureIncompleteException.class)
    ProblemDetail handleAccountErasureIncomplete(AccountErasureIncompleteException ex) {

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        problem.setProperty("code", INCOMPLETE_CODE);
        return problem;
    }

    /** 429: an export reads an entire Organization, so being throttled is an expected, retryable outcome. */
    @ExceptionHandler(ExportRateLimitedException.class)
    ResponseEntity<ProblemDetail> handleExportRateLimited(ExportRateLimitedException ex) {

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problem.setProperty("code", RATE_LIMITED_CODE);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds())).body(problem);
    }
}
