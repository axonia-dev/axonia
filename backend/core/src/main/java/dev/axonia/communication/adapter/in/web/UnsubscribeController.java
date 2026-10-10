package dev.axonia.communication.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.communication.application.NotificationPreferenceService;
import dev.axonia.communication.application.UnsubscribeClaim;
import dev.axonia.communication.application.UnsubscribeRateLimitedException;
import dev.axonia.communication.application.UnsubscribeRateLimiter;
import dev.axonia.communication.application.UnsubscribeTokens;
import dev.axonia.shared.api.tenancy.OrganizationScope;
import dev.axonia.shared.api.web.ClientAddress;
import lombok.RequiredArgsConstructor;

/**
 * The one-click unsubscribe a mail footer link and a {@code List-Unsubscribe-Post} header both point at. Lives OUTSIDE
 * {@code /axonia} on the {@code /public/**} surface the edge already proxies, on the same precedent as the other
 * anonymous endpoints: the security chain permits everything outside the app prefix, and holding a valid token is what
 * authorizes this write, never a sign-in - the whole point of a real unsubscribe is that it needs none.
 *
 * <p>
 * {@code POST}, not {@code GET}: a mail client's link-prefetcher issuing a {@code GET} to "check" the link must never
 * be the thing that unsubscribes somebody. {@code List-Unsubscribe-Post: List-Unsubscribe=One-Click} (set by
 * {@code SmtpDelivery} on the outgoing mail) is what tells a compliant client this is the one-click path.
 *
 * <p>
 * Answers {@code 204} whether the token verified, whether a row already matched, and whether anything actually changed.
 * A token that has already been redeemed must look identical, from the outside, to one redeemed for the first time -
 * and so must one that never verified at all - or the response itself becomes a way to probe a link's state without
 * holding it.
 */
@RestController
@RequestMapping("/public/notifications")
@RequiredArgsConstructor
public class UnsubscribeController {

    private final UnsubscribeTokens tokens;
    private final NotificationPreferenceService notificationPreferenceService;
    private final UnsubscribeRateLimiter rateLimiter;

    @PostMapping("/unsubscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unsubscribe(@RequestParam String token, HttpServletRequest request) {

        var attempt = rateLimiter.allow(ClientAddress.of(request));
        if (!attempt.allowed()) {
            throw new UnsubscribeRateLimitedException(attempt);
        }
        tokens.verify(token).ifPresent(this::apply);
    }

    /**
     * Runs the write inside the Organization the claim names, not the caller's own - there is no caller. {@code
     * OrganizationScope.runAs} must wrap the {@code @Transactional} entry point rather than sit inside it: {@code
     * app.current_org} is chosen when the transaction acquires its connection, so entering the scope here, before
     * {@code notificationPreferenceService.unsubscribe} is ever called, is what makes it visible to that acquisition.
     */
    private void apply(UnsubscribeClaim claim) {

        OrganizationScope.runAs(claim.organizationId(),
                () -> notificationPreferenceService.unsubscribe(claim.organizationId(), claim.userId(), claim.type()));
    }

    @ExceptionHandler(UnsubscribeRateLimitedException.class)
    ResponseEntity<ProblemDetail> handleRateLimited(UnsubscribeRateLimitedException ex) {

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds())).body(problem);
    }
}
