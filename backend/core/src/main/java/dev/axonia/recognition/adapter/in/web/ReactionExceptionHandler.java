package dev.axonia.recognition.adapter.in.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.axonia.recognition.domain.ReactionRateLimitedException;

/**
 * Module-scoped advice for {@link ReactionController}, keeping the reaction-proposal rate limit's mapping out of the
 * root {@code GlobalExceptionHandler} exactly as {@code PrivacyExceptionHandler} does for its own module.
 */
@RestControllerAdvice(assignableTypes = ReactionController.class)
class ReactionExceptionHandler {

    private static final String RATE_LIMITED_CODE = "REACTION_PROPOSAL_RATE_LIMITED";

    @ExceptionHandler(ReactionRateLimitedException.class)
    ResponseEntity<ProblemDetail> handleRateLimited(ReactionRateLimitedException ex) {

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problem.setProperty("code", RATE_LIMITED_CODE);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds())).body(problem);
    }
}
