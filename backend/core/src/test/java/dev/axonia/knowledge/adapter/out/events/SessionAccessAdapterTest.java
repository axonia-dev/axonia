package dev.axonia.knowledge.adapter.out.events;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.events.api.Session;
import dev.axonia.events.api.SessionStatus;
import dev.axonia.events.api.Sessions;
import dev.axonia.events.api.SlotSessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class SessionAccessAdapterTest {

    @Mock
    private Sessions sessionService;
    @Mock
    private SlotSessions slotSessionReader;

    private SessionAccessAdapter sut;

    @BeforeEach
    void setUp() {

        sut = new SessionAccessAdapter(sessionService, slotSessionReader);
    }

    @Test
    void session_mapsIdAndCommunityId() {

        // given
        when(sessionService.get(9L))
                .thenReturn(Session.builder().id(9L).communityId(5L).status(SessionStatus.SCHEDULED).build());

        // when
        var result = sut.session(9L);

        // then
        assertThat(result.id()).isEqualTo(9L);
        assertThat(result.communityId()).isEqualTo(5L);
        assertThat(result.held()).isFalse();
    }

    @Test
    void session_mapsHeldFromStatus() {

        // given
        when(sessionService.get(9L))
                .thenReturn(Session.builder().id(9L).communityId(5L).status(SessionStatus.HELD).build());

        // when
        var result = sut.session(9L);

        // then
        assertThat(result.held()).isTrue();
    }

    @Test
    void sessionIdOfSlot_delegatesToSlotSessionReader() {

        // given
        when(slotSessionReader.sessionIdOf(7L)).thenReturn(Optional.of(100L));

        // when
        var result = sut.sessionIdOfSlot(7L);

        // then
        assertThat(result).contains(100L);
    }
}
