package dev.axonia.installation.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import dev.axonia.communication.MailOutcome;
import dev.axonia.communication.MailServerSettings;
import dev.axonia.communication.TestMail;
import dev.axonia.communication.TestMailSender;
import dev.axonia.identity.UserDirectory;
import dev.axonia.identity.UserDirectory.DirectoryUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * One test mail with candidate values, outside any transaction: the send can take fifteen seconds and must never hold a
 * pooled connection while it does.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MailTestService {

    static final String SUBJECT = "Test mail from axonia";

    static final String UNNAMED_SENDER = "an administrator of this installation";

    private static final DateTimeFormatter WHEN = DateTimeFormatter
            .ofPattern("d MMM yyyy 'at' HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final MailServerResolver resolver;

    private final MailServerCandidates candidates;

    private final MailServerTestRateLimiter limiter;

    private final TestMailSender sender;

    private final TestTokens tokens;

    private final UserDirectory directory;

    private final Clock clock;

    MailTestResult test(MailTestRequest request, MailServerActor actor) {

        var recipient = request.recipient().strip();
        if (!MailServerSettings.isMailAddress(recipient)) {
            throw new MailServerFieldException("recipient", "must be one bare mail address.");
        }
        var resolved = resolver.resolve();
        var settings = candidates.settings(request.values().values(), resolved);
        var decision = limiter.allow(actor.subject());
        if (!decision.allowed()) {
            throw new MailTestRateLimitedException(decision.retryAfterSeconds());
        }
        var outcome = sender.send(settings, new TestMail(recipient, SUBJECT, text(actor, clock.instant())));
        log.info("Test mail by {} through {}:{}: {}", actor.subject(), settings.host(), settings.port(),
                outcome instanceof MailOutcome.Refused refused ? refused.reason().code() : "accepted");
        return switch (outcome) {
            case MailOutcome.Accepted _ -> {
                var acceptedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
                var issued = tokens.issue(
                        new TestTokens.Tested(settings.fingerprint(), resolved.row().revision(), actor.subject()),
                        acceptedAt);
                yield MailTestResult.builder().accepted(true).host(settings.host()).acceptedAt(acceptedAt)
                        .testToken(issued.token()).expiresAt(issued.expiresAt()).build();
            }
            case MailOutcome.Refused refused -> throw new MailTestRefusedException(refused.reason(), refused.reply());
        };
    }

    /**
     * Names the person from the directory, never by the identity provider's subject, which means nothing to a reader.
     */
    private String text(MailServerActor actor, Instant sentAt) {

        var name = directory.byIds(actor.organizationId(), List.of(actor.subject())).stream().findFirst()
                .map(DirectoryUser::displayName).orElse(UNNAMED_SENDER);
        return "This test mail was sent by " + name + " from the mail server settings, on " + WHEN.format(sentAt)
                + ".\nIf it arrived, the mail server accepts mail from this installation for this address.\n";
    }
}
