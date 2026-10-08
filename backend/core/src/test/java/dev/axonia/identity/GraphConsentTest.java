package dev.axonia.identity;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphConsentTest {

    /** A connection nobody has checked stores nothing, and that reads as UNKNOWN, never as a verdict. */
    @Test
    void storedOrUnknown_anUncheckedConnection() {

        // when
        var actual = GraphConsent.storedOrUnknown(null);

        // then
        assertThat(actual).isEqualTo(GraphConsent.UNKNOWN);
    }

    @Test
    void storedOrUnknown_everyStoredVerdictReadsBackAsItself() {

        // when
        var actual = Arrays.stream(GraphConsent.values()).map(consent -> GraphConsent.storedOrUnknown(consent.name()))
                .toList();

        // then
        assertThat(actual).containsExactly(GraphConsent.values());
    }

    /** A column holding something no verdict ever stored is corrupt, and fails loudly rather than posing as UNKNOWN. */
    @Test
    void storedOrUnknown_aValueNoVerdictStores() {

        // then
        assertThatThrownBy(() -> GraphConsent.storedOrUnknown("PENDING")).isInstanceOf(IllegalArgumentException.class);
    }
}
