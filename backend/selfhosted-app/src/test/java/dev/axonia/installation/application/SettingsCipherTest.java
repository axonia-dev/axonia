package dev.axonia.installation.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.axonia.installation.domain.SecretField;
import dev.axonia.installation.domain.TestKeys;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsCipherTest {

    private static final SecretField PASSWORD = SecretField.MAIL_PASSWORD;

    private final SettingsCipher cipher = new SettingsCipher(TestKeys.current());

    @Test
    void sealsUnderTheCurrentKeyAndOpensWhatItSealed() {

        // when
        var sealed = cipher.seal(PASSWORD, "s3cret");

        // then
        assertThat(sealed).startsWith("v1:bff04208:").doesNotContain("s3cret");
        assertThat(cipher.open(PASSWORD, sealed)).contains("s3cret");
        assertThat(cipher.sealedWithCurrentKey(sealed)).isTrue();
    }

    @Test
    void sealsTheSameSecretDifferentlyEachTime() {

        assertThat(cipher.seal(PASSWORD, "s3cret")).isNotEqualTo(cipher.seal(PASSWORD, "s3cret"));
    }

    @Test
    void opensNothingSealedForAnotherField() {

        // given
        var sealed = cipher.seal(PASSWORD, "s3cret");

        // when
        var actual = cipher.open(new SecretField("mail", "username"), sealed);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void readsAnEnvelopeAnEarlierReleaseSealed() {

        // given: sealed outside this code, nonce 01..0c; a stored format, never regenerated from it
        var stored = "v1:bff04208:AQIDBAUGBwgJCgsMDr5aFIlACcigBi7JAE8TUkVu7anI2g";

        // then
        assertThat(cipher.open(PASSWORD, stored)).contains("s3cret");
        assertThat(cipher.open(new SecretField("mail", "username"), stored)).isEmpty();
    }

    @Test
    void opensASecretSealedUnderAPreviousKeyAndResealsItUnderTheCurrentOne() {

        // given
        var sealedBefore = new SettingsCipher(TestKeys.previousOnly()).seal(PASSWORD, "s3cret");
        var rotated = new SettingsCipher(TestKeys.currentWithPrevious());

        // when
        var actual = rotated.resealed(PASSWORD, sealedBefore);

        // then
        assertThat(sealedBefore).startsWith("v1:e4caaa0c:");
        assertThat(rotated.open(PASSWORD, sealedBefore)).contains("s3cret");
        assertThat(rotated.sealedWithCurrentKey(sealedBefore)).isFalse();
        assertThat(actual).hasValueSatisfying(sealed -> {
            assertThat(sealed).startsWith("v1:bff04208:");
            assertThat(rotated.open(PASSWORD, sealed)).contains("s3cret");
        });
    }

    @Test
    void resealsNothingAlreadyUnderTheCurrentKeyOrUnreadable() {

        // given
        var current = cipher.seal(PASSWORD, "s3cret");
        var underAKeyNoLongerHeld = new SettingsCipher(TestKeys.previousOnly()).seal(PASSWORD, "s3cret");

        // then
        assertThat(cipher.resealed(PASSWORD, current)).isEmpty();
        assertThat(cipher.resealed(PASSWORD, underAKeyNoLongerHeld)).isEmpty();
    }

    @Test
    void holdsAsPreviousOnlyAKeyItHoldsBesideItsCurrentOne() {

        // given
        var rotated = new SettingsCipher(TestKeys.currentWithPrevious());

        // then
        assertThat(rotated.holdsAsPrevious(TestKeys.PREVIOUS_ID)).isTrue();
        assertThat(rotated.holdsAsPrevious(TestKeys.CURRENT_ID)).isFalse();
        assertThat(rotated.holdsAsPrevious("0badc0de")).isFalse();
        assertThat(rotated.holdsAsPrevious(null)).isFalse();
    }

    @Test
    void opensNothingSealedUnderAKeyItNoLongerHolds() {

        // given
        var sealed = new SettingsCipher(TestKeys.previousOnly()).seal(PASSWORD, "s3cret");

        // when
        var actual = cipher.open(PASSWORD, sealed);

        // then
        assertThat(actual).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "s3cret", "v2:bff04208:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "v1:bff04208",
            "v1:bff04208:%%%%", "v1:bff04208:AAAAAAAAAAAAAAAA", "v1:bff04208:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void opensNothingThatIsNotAnEnvelopeItSealed(String envelope) {

        assertThat(cipher.open(PASSWORD, envelope)).isEmpty();
    }
}
