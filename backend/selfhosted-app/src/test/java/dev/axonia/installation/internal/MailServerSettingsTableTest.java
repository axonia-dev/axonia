package dev.axonia.installation.internal;

import java.sql.SQLException;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.axonia.communication.MailSecurity;
import dev.axonia.selfhosted.SelfHostedDatabase;
import dev.axonia.selfhosted.SelfHostedSpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** The row as the runtime role reads and writes it. */
@SelfHostedSpringBootTest
class MailServerSettingsTableTest {

    private static final Instant FIRST = Instant.parse("2026-10-04T10:00:00.123Z");

    @Autowired
    private MailServerSettingsTable table;

    @BeforeEach
    void reset() throws SQLException {

        MailServerRows.reset();
    }

    @Test
    void readsEveryColumnOfTheRow() throws SQLException {

        // given
        MailServerRows.set("host = 'smtp.acme.test', port = 587, security = 'STARTTLS', username = 'relay',"
                + " password_secret = 'v1:bff04208:x', from_address = 'axonia@acme.test', from_name = 'Acme',"
                + " tested_fingerprint = 'v1:bff04208:y', last_failure_reason = 'timeout',"
                + " last_failure_at = '2026-10-04T10:00:00.123Z'");

        // when
        var actual = table.read();

        // then
        assertThat(actual).usingRecursiveComparison().ignoringFields("revision")
                .isEqualTo(MailServerRow.builder().host("smtp.acme.test").port(587).security(MailSecurity.STARTTLS)
                        .username("relay").passwordSecret("v1:bff04208:x").fromAddress("axonia@acme.test")
                        .fromName("Acme").testedFingerprint("v1:bff04208:y").lastFailureReason("timeout")
                        .lastFailureAt(FIRST).build());
        assertThat(table.watermark())
                .isEqualTo(new MailServerSettingsTable.Watermark(actual.revision(), "timeout", "bff04208", "bff04208"));
    }

    @Test
    void readsNoKeyIdentifierWhereNothingIsSealed() throws SQLException {

        // when
        var actual = table.watermark();

        // then
        assertThat(actual.passwordKeyId()).isNull();
        assertThat(actual.fingerprintKeyId()).isNull();
    }

    @Test
    void replacesAStoredSecretOnlyIfItIsStillTheOneRead() throws SQLException {

        // given
        MailServerRows.set("password_secret = 'v1:e4caaa0c:old'");
        var revision = table.read().revision();

        // when
        var otherEnvelope = table.replacePasswordSecret(revision, "v1:e4caaa0c:other", "v1:bff04208:new");
        var otherRevision = table.replacePasswordSecret(revision + 1, "v1:e4caaa0c:old", "v1:bff04208:new");
        var replaced = table.replacePasswordSecret(revision, "v1:e4caaa0c:old", "v1:bff04208:new");

        // then
        assertThat(otherEnvelope).isFalse();
        assertThat(otherRevision).as("a save moved the row since it was read").isFalse();
        assertThat(replaced).isTrue();
        assertThat(table.read().passwordSecret()).isEqualTo("v1:bff04208:new");
        assertThat(table.read().revision()).as("a re-seal moves no revision").isEqualTo(revision);
    }

    @Test
    void replacesATestedFingerprintOnlyIfItIsStillTheOneRead() throws SQLException {

        // given
        MailServerRows.set("tested_fingerprint = 'v1:e4caaa0c:old'");
        var revision = table.read().revision();

        // then
        assertThat(table.replaceTestedFingerprint(revision, "v1:e4caaa0c:other", "v1:bff04208:new")).isFalse();
        assertThat(table.replaceTestedFingerprint(revision + 1, "v1:e4caaa0c:old", "v1:bff04208:new")).isFalse();
        assertThat(table.replaceTestedFingerprint(revision, "v1:e4caaa0c:old", "v1:bff04208:new")).isTrue();
        assertThat(table.read().testedFingerprint()).isEqualTo("v1:bff04208:new");
    }

    @Test
    void keepsWhenFailuresBeganAcrossReasonsAndForgetsItOnceMailIsAccepted() throws SQLException {

        // given
        var revision = table.read().revision();

        // when
        var first = table.recordFailure("timeout", FIRST, revision);
        var again = table.recordFailure("timeout", FIRST.plusSeconds(60), revision);
        var other = table.recordFailure("authentication_failed", FIRST.plusSeconds(120), revision);
        var failing = table.read();
        var cleared = table.clearFailure(revision);
        var clearedAgain = table.clearFailure(revision);

        // then
        assertThat(first).isTrue();
        assertThat(again).isFalse();
        assertThat(other).isTrue();
        assertThat(failing.lastFailureReason()).isEqualTo("authentication_failed");
        assertThat(failing.lastFailureAt()).isEqualTo(FIRST);
        assertThat(failing.revision()).as("a delivery state is not a change of settings").isEqualTo(revision);
        assertThat(cleared).isTrue();
        assertThat(clearedAgain).isFalse();
        assertThat(SelfHostedDatabase.countAsOwner(
                "SELECT count(*) FROM mail_server_settings WHERE last_failure_at IS NULL AND last_failure_reason IS NULL"))
                .isOne();
    }

    /** A delivery state is about the settings of one revision; a save that moved it meanwhile keeps its own. */
    @Test
    void writesADeliveryStateOnlyOnTheRevisionItIsAbout() throws SQLException {

        // given
        var aboutRevision = table.read().revision();
        MailServerRows.set("last_failure_reason = 'timeout', last_failure_at = '2026-10-04T10:00:00.123Z'");

        // when
        var recorded = table.recordFailure("authentication_failed", FIRST, aboutRevision);
        var cleared = table.clearFailure(aboutRevision);

        // then
        assertThat(recorded).isFalse();
        assertThat(cleared).isFalse();
        assertThat(table.read().lastFailureReason()).isEqualTo("timeout");
    }

    @Test
    void writesASaveOnTheRevisionItReadAndNeverOnAnother() throws SQLException {

        // given
        MailServerRows.set("last_failure_reason = 'timeout', last_failure_at = now()");
        var before = table.read();
        var next = MailServerRow.builder().host("smtp.acme.test").port(25).security(MailSecurity.NONE)
                .fromAddress("axonia@acme.test").testedFingerprint("v1:bff04208:t").revision(before.revision()).build();

        // when
        var written = table.write(next, FIRST);
        var stale = table.write(next, FIRST);

        // then
        assertThat(written).isTrue();
        assertThat(stale).isFalse();
        assertThat(table.read()).isEqualTo(next.toBuilder().revision(before.revision() + 1).build());
        assertThat(SelfHostedDatabase.countAsOwner(
                "SELECT count(*) FROM mail_server_settings" + " WHERE updated_at = '2026-10-04T10:00:00.123Z'"))
                .isOne();
    }
}
