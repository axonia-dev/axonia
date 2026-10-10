package dev.axonia.community.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.axonia.communication.api.ChatSendFailedException;

/**
 * Module-scoped advice, keeping {@code communication}'s cross-module exception type out of the root
 * {@code GlobalExceptionHandler} - exactly as {@code PrivacyExceptionHandler} already does for its own downstream
 * failures, and for the same reason: every type the root handler maps lives in the shared kernel already, and this one
 * does not.
 *
 * <p>
 * 502, not 500: a failed test post means Slack, Teams, or the network between here and them refused or timed out -
 * axonia itself did not break, and the same call is safe to retry. The message is exactly the one
 * {@code ChatDestination.lastError} already shows a leader on the settings page, so it never carries more than a host
 * name - see {@code ChatTransport}'s own javadoc for why that is safe to return.
 *
 * <p>
 * {@code assignableTypes} names only {@link ChatDestinationController}: {@link ChatSendFailedException} is raised
 * exclusively by {@code ChatChannelProbeAdapter}, under {@code ChatDestinationService.test}, which that controller's
 * {@code /test} endpoint alone reaches. {@code CommunityController} has no call path that can throw it.
 */
@RestControllerAdvice(assignableTypes = ChatDestinationController.class)
class CommunityExceptionHandler {

    private static final String CHAT_SEND_FAILED_CODE = "CHAT_SEND_FAILED";

    @ExceptionHandler(ChatSendFailedException.class)
    ProblemDetail handleChatSendFailed(ChatSendFailedException ex) {

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        problem.setProperty("code", CHAT_SEND_FAILED_CODE);
        return problem;
    }
}
