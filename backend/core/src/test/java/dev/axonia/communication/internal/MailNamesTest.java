package dev.axonia.communication.internal;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.mail.MessagingException;

import org.junit.jupiter.api.Test;

import dev.axonia.communication.MailRefusal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class MailNamesTest {

    /**
     * The pattern the scan replaced, kept here only as the reference it must agree with: it nests one quantifier inside
     * another and backtracks without end on a long dotted or hyphenated run.
     */
    private static final Pattern REPLACED = Pattern.compile("<[^>]*(>|$)|\\S+@\\S+|[\\w-]+(\\.[\\w-]+)+");

    @Test
    void blanksExactlyWhatTheReplacedPatternBlanked() {

        var messages = List.of("", "Couldn't connect to host, port: tls.acme.invalid, 587; timeout 2000",
                "550 5.7.1 <tls@acme.com>: Sender rejected", "550 5.7.1 ssl@acme.com: Sender rejected",
                "Unknown SMTP host: smtp.ssl.example.org", "STARTTLS is required but host does not support STARTTLS",
                "<unclosed tls.acme", "<x>a@b", "<x>a<y>b@c", "a>b@c", "a@", "@a", "@a@b", "a@b@", "x.y@z", "a..b.c",
                "a.", ".a", "-a.b-", "a.b!c.d!e", "__.__ x", "1.2.3.4:25", "tab\ttls.host\u000Bv@w\fend\r",
                "TLSv1.3 handshake", "<>", "<<a>", "a b.c d@e f");

        assertThat(messages).allSatisfy(message -> assertThat(MailNames.blankedIn(message))
                .isEqualTo(REPLACED.matcher(message).replaceAll(" ")));
    }

    /** A reply no server would send, shaped to make a backtracking pattern take minutes; the scan takes one pass. */
    @Test
    void classifiesALongAdversarialReplyAtOnce() {

        var replies = List.of("a" + ".a".repeat(50_000) + "!", "-".repeat(50_000), "<>a".repeat(20_000),
                "a ".repeat(50_000), "@".repeat(50_000));

        assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> assertThat(replies)
                        .allSatisfy(reply -> assertThat(MailFailures.reasonOf(new MessagingException(reply)))
                                .isEqualTo(MailRefusal.CONNECTION_FAILED)));
    }
}
