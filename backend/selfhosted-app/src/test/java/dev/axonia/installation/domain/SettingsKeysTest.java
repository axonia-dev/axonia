package dev.axonia.installation.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingsKeysTest {

    @Test
    void namesEachKeyByAnIdentifierDerivedFromIt() {

        // when
        var actual = TestKeys.currentWithPrevious();

        // then
        assertThat(actual.current().id()).isEqualTo(TestKeys.CURRENT_ID);
        assertThat(actual.byId(TestKeys.PREVIOUS_ID))
                .hasValueSatisfying(key -> assertThat(key.id()).isEqualTo(TestKeys.PREVIOUS_ID));
        assertThat(actual.byId(TestKeys.CURRENT_ID)).containsSame(actual.current());
        assertThat(actual.byId("00000000")).isEmpty();
    }

    @Test
    void readsAKeyWithSurroundingSpaceAndPreviousKeysSeparatedByCommas() {

        // when
        var actual = SettingsKeys.parse(" " + TestKeys.CURRENT + "\n",
                " " + TestKeys.PREVIOUS + " ," + TestKeys.CURRENT);

        // then
        assertThat(actual.current().id()).isEqualTo(TestKeys.CURRENT_ID);
        assertThat(actual.byId(TestKeys.PREVIOUS_ID)).isPresent();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "not base64 at all", "c2hvcnQ=", "dGVzdC1sb2NhbC1zZXR0aW5ncy1rZXktMzJieXRlcyEh"})
    void refusesACurrentKeyThatIsNot32BytesOfBase64AndNeverPrintsIt(String key) {

        assertThatThrownBy(() -> SettingsKeys.parse(key, "")).isInstanceOf(IllegalStateException.class).hasMessage(
                "AXONIA_SETTINGS_KEY must be 32 random bytes in base64, as openssl rand -base64 32 prints them.");
    }

    @Test
    void refusesAPreviousKeyAndNamesItsPositionNotItsValue() {

        assertThatThrownBy(() -> SettingsKeys.parse(TestKeys.CURRENT, TestKeys.PREVIOUS + ",c2hvcnQ="))
                .isInstanceOf(IllegalStateException.class).hasMessage(
                        "AXONIA_SETTINGS_PREVIOUS_KEYS holds a key that is not 32 random bytes in base64 (entry 2).");
    }

    @Test
    void readsNoPreviousKeyFromNothing() {

        assertThat(SettingsKeys.parse(TestKeys.CURRENT, null).byId(TestKeys.PREVIOUS_ID)).isEmpty();
    }

    @Test
    void printsIdentifiersAndNeverAKey() {

        // when
        var actual = TestKeys.currentWithPrevious().toString() + TestKeys.current().current();

        // then
        assertThat(actual).isEqualTo("SettingsKeys[current=bff04208, previous=1]SettingsKey[bff04208]")
                .doesNotContain(TestKeys.CURRENT).doesNotContain("test-local");
    }
}
