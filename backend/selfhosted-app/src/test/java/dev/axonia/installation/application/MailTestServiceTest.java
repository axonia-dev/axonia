package dev.axonia.installation.application;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.axonia.SteppableClock;
import dev.axonia.communication.api.MailOutcome;
import dev.axonia.communication.api.MailSecurity;
import dev.axonia.communication.api.MailServerSettings;
import dev.axonia.communication.api.TestMail;
import dev.axonia.communication.api.TestMailSender;
import dev.axonia.identity.api.UserDirectory;
import dev.axonia.installation.domain.MailServerRow;
import dev.axonia.installation.domain.MailValues;
import dev.axonia.installation.domain.TestKeys;
import dev.axonia.shared.api.ratelimit.RateLimitDecision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** The test mail's body, which goes to any address the person types. */
class MailTestServiceTest {

    private static final String SUBJECT = "f3b1c2d4-oid-of-the-identity-provider";

    private final MailServerResolver resolver = mock(MailServerResolver.class);

    private final MailServerCandidates candidates = mock(MailServerCandidates.class);

    private final MailServerTestRateLimiter limiter = mock(MailServerTestRateLimiter.class);

    private final TestMailSender sender = mock(TestMailSender.class);

    private final UserDirectory directory = mock(UserDirectory.class);

    private final SteppableClock clock = new SteppableClock(Instant.parse("2026-10-04T10:00:00.123Z"));

    private final MailTestService service = new MailTestService(resolver, candidates, limiter, sender,
            new TestTokens(TestKeys.current(), clock), directory, clock);

    /** Before the directory knows the person, the mail never names their subject. */
    @Test
    void namesAPersonTheDirectoryDoesNotKnowYetWithoutTheirSubject() {

        // given
        var resolved = new ResolvedMailServer(MailValues.NONE, MailValues.NONE, false,
                MailServerRow.builder().revision(3).build());
        given(resolver.resolve()).willReturn(resolved);
        given(candidates.settings(any(), any())).willReturn(MailServerSettings.builder().host("smtp.acme.test").port(25)
                .security(MailSecurity.NONE).fromAddress("axonia@acme.test").build());
        given(limiter.allow(SUBJECT)).willReturn(RateLimitDecision.allow());
        given(directory.byIds("acme", List.of(SUBJECT))).willReturn(List.of());
        given(sender.send(any(), any())).willReturn(new MailOutcome.Accepted());
        var request = new MailTestRequest(
                new MailServerValuesRequest("smtp.acme.test", 25, "NONE", null, null, "axonia@acme.test", null),
                "ana@acme.test");

        // when
        service.test(request, new MailServerActor(SUBJECT, "acme"));

        // then
        var sent = ArgumentCaptor.forClass(TestMail.class);
        verify(sender).send(any(), sent.capture());
        assertThat(sent.getValue().text()).contains("sent by an administrator of this installation")
                .doesNotContain(SUBJECT);
    }
}
