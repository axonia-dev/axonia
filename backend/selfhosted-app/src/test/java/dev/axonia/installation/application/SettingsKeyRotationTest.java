package dev.axonia.installation.application;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.communication.api.MailServerState;
import dev.axonia.installation.adapter.out.persistence.MailServerRows;
import dev.axonia.installation.adapter.out.persistence.MailServerSettingsTable;
import dev.axonia.installation.application.port.out.MailServerSettingsStore;
import dev.axonia.installation.configuration.MailServerOverlayProperties;
import dev.axonia.installation.domain.MailServerOverlay;
import dev.axonia.installation.domain.MailServerWatermark;
import dev.axonia.installation.domain.SecretField;
import dev.axonia.installation.domain.SettingsKeys;
import dev.axonia.installation.domain.TestKeys;
import dev.axonia.selfhosted.SelfHostedSpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * A start after the key changed, against the real row: the installation's own context holds only the current test key,
 * so the rotating instance is assembled here with the previous one as well.
 */
@SelfHostedSpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class SettingsKeyRotationTest {

    private static final MailServerOverlay NO_OVERLAY = MailServerOverlay
            .from(MailServerOverlayProperties.builder().build());

    private final SettingsKeys rotatedKeys = TestKeys.currentWithPrevious();

    private final SettingsCipher rotatedCipher = new SettingsCipher(rotatedKeys);

    private final TestedFingerprints rotatedFingerprints = new TestedFingerprints(rotatedKeys);

    @Autowired
    private MailServerSettingsTable table;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private SettingsKeyRotation rotation;

    @BeforeEach
    void storeUnderThePreviousKey() throws SQLException {

        MailServerRows.reset();
        rotation = new SettingsKeyRotation(table, rotatedCipher, rotatedFingerprints,
                new MailServerResolver(table, rotatedCipher, NO_OVERLAY));
    }

    @Test
    void resealsThePasswordAndRekeysTheTestedFingerprintUnderTheCurrentKey(CapturedOutput output) throws SQLException {

        // given
        var oldCipher = new SettingsCipher(TestKeys.previousOnly());
        MailServerRows.set("host = 'smtp.acme.test', port = 587, security = 'STARTTLS', username = 'relay',"
                + " from_address = 'axonia@acme.test', password_secret = '"
                + oldCipher.seal(SecretField.MAIL_PASSWORD, "s3cret") + "'");
        var fingerprint = ((MailServerState.Configured) new MailServerResolver(table, rotatedCipher, NO_OVERLAY)
                .resolve().state()).settings().fingerprint();
        MailServerRows.set(
                "tested_fingerprint = '" + new TestedFingerprints(TestKeys.previousOnly()).digest(fingerprint) + "'");
        var revision = table.read().revision();

        // when
        rotation.sealUnderTheCurrentKey();

        // then
        var actual = table.read();
        assertThat(actual.passwordSecret()).startsWith("v1:" + TestKeys.CURRENT_ID + ":");
        assertThat(new SettingsCipher(TestKeys.current()).open(SecretField.MAIL_PASSWORD, actual.passwordSecret()))
                .contains("s3cret");
        assertThat(actual.testedFingerprint())
                .isEqualTo(new TestedFingerprints(TestKeys.current()).digest(fingerprint));
        assertThat(actual.revision()).as("re-sealing changes no value").isEqualTo(revision);
        assertThat(output).containsOnlyOnce(
                "Re-sealed the stored mail server password from settings key e4caaa0c to settings key bff04208.");
        assertThat(output).doesNotContain("s3cret");
    }

    /** Two instances starting, or checking, together: only the one whose conditional update wins says it re-sealed. */
    @Test
    void saysNothingWhenAnotherInstanceReSealedFirst(CapturedOutput output) throws SQLException {

        // given
        MailServerRows.set("username = 'relay', password_secret = '"
                + new SettingsCipher(TestKeys.previousOnly()).seal(SecretField.MAIL_PASSWORD, "s3cret") + "'");
        var overtaken = new MailServerSettingsTable(jdbc) {

            @Override
            public boolean replacePasswordSecret(long revision, String sealedBefore, String sealedNow) {

                return false;
            }
        };

        // when
        new SettingsKeyRotation(overtaken, rotatedCipher, rotatedFingerprints,
                new MailServerResolver(overtaken, rotatedCipher, NO_OVERLAY)).sealUnderTheCurrentKey();

        // then
        assertThat(output).doesNotContain("Re-sealed the stored mail server password");
    }

    /** A check re-seals a row it reads under another key it holds, and only such a row. */
    @Test
    void aCheckResealsOnlyARowUnderAnotherKeyItHolds(CapturedOutput output) throws SQLException {

        // given
        var unknown = "v1:0badc0de:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        MailServerRows.set("username = 'relay', password_secret = '" + unknown + "', tested_fingerprint = '"
                + new TestedFingerprints(TestKeys.previousOnly()).digest("any values") + "'");

        // when
        rotation.sealUnderTheCurrentKey(table.watermark());
        var underAKeyNoneHolds = table.read().passwordSecret();
        MailServerRows.set("password_secret = '"
                + new SettingsCipher(TestKeys.previousOnly()).seal(SecretField.MAIL_PASSWORD, "s3cret") + "'");
        rotation.sealUnderTheCurrentKey(table.watermark());

        // then
        assertThat(underAKeyNoneHolds).as("nothing it could open, and nothing said at every check").isEqualTo(unknown);
        assertThat(output).doesNotContain("an Owner must enter it again");
        assertThat(table.read().passwordSecret()).startsWith("v1:" + TestKeys.CURRENT_ID + ":");
    }

    @Test
    void aCheckReadingTheRowUnderTheCurrentKeyReadsNothingMore() {

        // given
        var table = mock(MailServerSettingsStore.class);
        var checking = new SettingsKeyRotation(table, rotatedCipher, rotatedFingerprints,
                new MailServerResolver(table, rotatedCipher, NO_OVERLAY));

        // when
        checking.sealUnderTheCurrentKey(new MailServerWatermark(4, null, TestKeys.CURRENT_ID, TestKeys.CURRENT_ID));
        checking.sealUnderTheCurrentKey(new MailServerWatermark(4, null, null, null));

        // then
        verifyNoInteractions(table);
    }

    @Test
    void aCheckReKeysATestedFingerprintUnderAnotherKeyItHolds() throws SQLException {

        // given
        MailServerRows.set("host = 'smtp.acme.test', port = 25, security = 'NONE', from_address = 'axonia@acme.test'");
        var fingerprint = ((MailServerState.Configured) new MailServerResolver(table, rotatedCipher, NO_OVERLAY)
                .resolve().state()).settings().fingerprint();
        MailServerRows.set(
                "tested_fingerprint = '" + new TestedFingerprints(TestKeys.previousOnly()).digest(fingerprint) + "'");

        // when
        rotation.sealUnderTheCurrentKey(table.watermark());

        // then
        assertThat(table.read().testedFingerprint())
                .isEqualTo(new TestedFingerprints(TestKeys.current()).digest(fingerprint));
    }

    /**
     * Settings delivery would refuse, stored by a direct edit, fail neither a start nor a check. The shared context's
     * own check logs its refusal of the same row with another opening, so only this one's wording is counted.
     */
    @Test
    void storedSettingsDeliveryWouldRefuseLeaveTheFingerprintAndFailNothing(CapturedOutput output) throws SQLException {

        // given
        var digest = new TestedFingerprints(TestKeys.previousOnly()).digest("any values");
        MailServerRows.set("host = 'smtp\u3000acme.test', port = 25, security = 'NONE',"
                + " from_address = 'axonia@acme.test', tested_fingerprint = '" + digest + "'");

        // when
        rotation.sealUnderTheCurrentKey();
        rotation.sealUnderTheCurrentKey(table.watermark());
        MailServerRows.set("from_name = NULL");
        rotation.sealUnderTheCurrentKey(table.watermark());

        // then
        assertThat(table.read().testedFingerprint()).isEqualTo(digest);
        assertThat(output).as("said once, at the start, not at every check")
                .containsOnlyOnce("The tested fingerprint keeps its key");
    }

    /**
     * An instance still on the previous key flips the password back with no revision move, and a fingerprint no check
     * can re-key does not stop the next check from re-sealing it again.
     */
    @Test
    void aCheckReSealsAgainAPasswordAnInstanceOnTheOtherKeyFlippedBack() throws SQLException {

        // given
        var previousCipher = new SettingsCipher(TestKeys.previousOnly());
        MailServerRows.set("host = 'smtp.acme.test', port = 587, security = 'NONE', username = 'relay',"
                + " from_address = 'axonia@acme.test', password_secret = '"
                + previousCipher.seal(SecretField.MAIL_PASSWORD, "s3cret") + "', tested_fingerprint = '"
                + new TestedFingerprints(TestKeys.previousOnly()).digest("other values") + "'");
        rotation.sealUnderTheCurrentKey(table.watermark());
        var resealed = table.read();
        var flippedBack = table.replacePasswordSecret(resealed.revision(), resealed.passwordSecret(),
                previousCipher.seal(SecretField.MAIL_PASSWORD, "s3cret"));

        // when
        rotation.sealUnderTheCurrentKey(table.watermark());

        // then
        var actual = table.read();
        assertThat(resealed.passwordSecret()).startsWith("v1:" + TestKeys.CURRENT_ID + ":");
        assertThat(flippedBack).isTrue();
        assertThat(actual.revision()).as("the flip-back moved no revision").isEqualTo(resealed.revision());
        assertThat(actual.passwordSecret()).as("re-sealed again at the next check")
                .startsWith("v1:" + TestKeys.CURRENT_ID + ":");
    }

    /** A password the deployment sets makes the stored one irrelevant, so no Owner is told to enter it again. */
    @Test
    void asksForAPasswordNoKeyOpensOnlyWhenTheDeploymentDoesNotSetOne(CapturedOutput output) throws SQLException {

        // given
        var unknown = "v1:0badc0de:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        MailServerRows.set("username = 'relay', password_secret = '" + unknown + "'");
        var deploymentPassword = MailServerOverlay
                .from(MailServerOverlayProperties.builder().password("from-the-deployment").build());
        var locked = new SettingsKeyRotation(table, rotatedCipher, rotatedFingerprints,
                new MailServerResolver(table, rotatedCipher, deploymentPassword));

        // when
        locked.sealUnderTheCurrentKey();
        var saidWhileLocked = output.getAll();
        rotation.sealUnderTheCurrentKey();

        // then
        assertThat(saidWhileLocked).doesNotContain("an Owner must enter it again");
        assertThat(output.getAll()).contains("an Owner must enter it again");
    }

    @Test
    void leavesAPasswordNoKeyOpensAsItIs() throws SQLException {

        // given
        var unknown = "v1:0badc0de:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        MailServerRows.set("username = 'relay', password_secret = '" + unknown + "'");

        // when
        rotation.sealUnderTheCurrentKey();

        // then
        assertThat(table.read().passwordSecret()).isEqualTo(unknown);
    }

    @Test
    void changesNothingAlreadyUnderTheCurrentKeyOrNothingStored(CapturedOutput output) throws SQLException {

        // given
        var current = rotatedCipher.seal(SecretField.MAIL_PASSWORD, "s3cret");
        MailServerRows.set("username = 'relay', password_secret = '" + current + "', tested_fingerprint = 'v1:"
                + TestKeys.CURRENT_ID + ":unchanged'");

        // when
        rotation.sealUnderTheCurrentKey();
        var afterCurrent = table.read();
        MailServerRows.reset();
        rotation.sealUnderTheCurrentKey();

        // then
        assertThat(afterCurrent.passwordSecret()).isEqualTo(current);
        assertThat(output).doesNotContain("Re-sealed the stored mail server password");
        assertThat(afterCurrent.testedFingerprint()).isEqualTo("v1:" + TestKeys.CURRENT_ID + ":unchanged");
        assertThat(table.read().passwordSecret()).isNull();
    }

    @Test
    void leavesAFingerprintOfValuesTheDeploymentChangedSinceTheTest() throws SQLException {

        // given
        MailServerRows.set("host = 'smtp.acme.test', port = 587, security = 'NONE', from_address = 'axonia@acme.test',"
                + " tested_fingerprint = '" + new TestedFingerprints(TestKeys.previousOnly()).digest("other values")
                + "'");
        var stored = table.read().testedFingerprint();

        // when
        rotation.sealUnderTheCurrentKey();

        // then
        assertThat(table.read().testedFingerprint()).isEqualTo(stored);
    }
}
