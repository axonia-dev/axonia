package dev.axonia.knowledge.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.knowledge.application.port.out.SessionAccess;
import dev.axonia.knowledge.domain.SessionRef;

import static org.assertj.core.api.Assertions.assertThat;

@MockitoSettings
class SessionProvenanceGuardTest {

    @Mock
    private SessionAccess sessionAccess;

    private SessionProvenanceGuard sut;

    @BeforeEach
    void setUp() {

        sut = new SessionProvenanceGuard(sessionAccess);
    }

    @Test
    void isHeld_true() {

        assertThat(sut.isHeld(new SessionRef(100L, 1L, true))).isTrue();
    }

    @Test
    void isHeld_false() {

        assertThat(sut.isHeld(new SessionRef(100L, 1L, false))).isFalse();
    }

    /**
     * A {@code null} reference is not this method's to interpret -
     * {@link SessionProvenanceGuard#requireSessionInCommunity} is the one place that decides whether a missing session
     * id is legal - so {@code isHeld} fails closed to "not held" rather than unboxing a {@code null} into an NPE.
     */
    @Test
    void isHeld_nullSessionFailsClosed() {

        assertThat(sut.isHeld(null)).isFalse();
    }
}
