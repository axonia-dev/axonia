package dev.axonia.identity.internal;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.identity.UserDirectory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class PresenceSignalsAdapterTest {

    private static final String ORGANIZATION = "default";

    @Mock
    private UserDirectory userDirectory;

    @InjectMocks
    private PresenceSignalsAdapter sut;

    @Test
    void presentOf_delegatesToUserDirectory() {

        // given one contributor still here, one who has left
        when(userDirectory.presentOf(ORGANIZATION, Set.of("still-here", "departed"))).thenReturn(Set.of("still-here"));

        // when
        var result = sut.presentOf(ORGANIZATION, Set.of("still-here", "departed"));

        // then
        assertThat(result).containsExactly("still-here");
    }
}
