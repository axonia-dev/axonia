package dev.axonia.installation.adapter.in.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.axonia.installation.application.MailTestRateLimitedException;
import dev.axonia.installation.application.MailTestRefusedException;
import dev.axonia.installation.application.UntestedValuesException;
import dev.axonia.installation.domain.MailServerFieldException;

@RestControllerAdvice(assignableTypes = MailServerController.class)
class MailServerExceptionHandler {

    private static final String CODE = "code";

    /** The reason is the code; the server's own reply line, when it sent one, is the detail. */
    @ExceptionHandler(MailTestRefusedException.class)
    ProblemDetail refused(MailTestRefusedException refused) {

        var problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setProperty(CODE, refused.getReason().code());
        refused.getReply().ifPresent(problem::setDetail);
        return problem;
    }

    @ExceptionHandler(UntestedValuesException.class)
    ProblemDetail untested(UntestedValuesException untested) {

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, untested.getMessage());
        problem.setProperty(CODE, "untested");
        return problem;
    }

    @ExceptionHandler(MailTestRateLimitedException.class)
    ResponseEntity<ProblemDetail> rateLimited(MailTestRateLimitedException limited) {

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, limited.getMessage());
        problem.setProperty(CODE, "mail_test_rate_limited");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(limited.getRetryAfterSeconds())).body(problem);
    }

    @ExceptionHandler(MailServerFieldException.class)
    ProblemDetail fieldInvalid(MailServerFieldException invalid) {

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, invalid.getMessage());
        problem.setProperty(CODE, "field_invalid");
        problem.setProperty("field", invalid.getField());
        return problem;
    }
}
