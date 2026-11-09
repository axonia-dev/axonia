package dev.axonia.events.adapter.in.web;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Module-scoped advice for the events controllers. Two writes to one topic or session can race, a speaker write against
 * an edit or a status change; the one that loses on the version column is told to reload, not handed a 500.
 */
@RestControllerAdvice(basePackageClasses = TopicController.class)
class EventsExceptionHandler {

    static final String CONCURRENT_UPDATE_CODE = "CONCURRENT_UPDATE";

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleLostRace(OptimisticLockingFailureException ex) {

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Someone else changed this at the same time. Reload and try again.");
        problem.setProperty("code", CONCURRENT_UPDATE_CODE);
        return problem;
    }
}
