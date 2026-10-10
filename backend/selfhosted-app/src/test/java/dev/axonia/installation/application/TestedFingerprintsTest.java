package dev.axonia.installation.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.axonia.installation.domain.TestKeys;

import static org.assertj.core.api.Assertions.assertThat;

class TestedFingerprintsTest {

    private final TestedFingerprints fingerprints = new TestedFingerprints(TestKeys.current());

    @Test
    void digestsAFingerprintUnderTheCurrentKeyAndKeepsNothingOfIt() {

        // when
        var actual = fingerprints.digest("fingerprint-a");

        // then
        assertThat(actual).startsWith("v1:bff04208:").doesNotContain("fingerprint-a").hasSize(12 + 43);
        assertThat(fingerprints.matches(actual, "fingerprint-a")).isTrue();
        assertThat(fingerprints.matches(actual, "fingerprint-b")).isFalse();
    }

    @Test
    void aTestedFingerprintKeepsItsStoredDigest() {

        // given: digested outside this code; a stored format, never regenerated from it
        var stored = "v1:bff04208:sQsadnZRtTQkRsitr1Ta_H6QZXp82KAhPXoGWS7yKZU";

        // then
        assertThat(fingerprints.digest("fingerprint-a")).isEqualTo(stored);
        assertThat(fingerprints.matches(stored, "fingerprint-a")).isTrue();
    }

    @Test
    void matchesADigestOfAPreviousKeyAndRekeysIt() {

        // given
        var before = new TestedFingerprints(TestKeys.previousOnly()).digest("fingerprint-a");
        var rotated = new TestedFingerprints(TestKeys.currentWithPrevious());

        // then
        assertThat(rotated.matches(before, "fingerprint-a")).isTrue();
        assertThat(rotated.rekeyed(before, "fingerprint-a")).contains(rotated.digest("fingerprint-a"));
        assertThat(rotated.rekeyed(before, "fingerprint-b")).isEmpty();
        assertThat(rotated.rekeyed(rotated.digest("fingerprint-a"), "fingerprint-a")).isEmpty();
    }

    @Test
    void matchesNothingUnderAKeyItNoLongerHolds() {

        // given
        var before = new TestedFingerprints(TestKeys.previousOnly()).digest("fingerprint-a");

        // then
        assertThat(fingerprints.matches(before, "fingerprint-a")).isFalse();
        assertThat(fingerprints.rekeyed(before, "fingerprint-a")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "fingerprint-a", "v2:bff04208:x", "v1:bff04208"})
    void matchesNothingThatIsNotADigest(String stored) {

        assertThat(fingerprints.matches(stored, "fingerprint-a")).isFalse();
    }
}
