package dev.axonia.events.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.NotFoundException;
import dev.axonia.communication.ChatChannelStatus;
import dev.axonia.communication.ChatDestinationsPort.ChatFailure;
import dev.axonia.communication.ChatFailureKind;
import dev.axonia.communication.MessageType;
import dev.axonia.events.CommunityAccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code sessionSubjectResolver} and {@code communityAccess} are mocked directly here rather than wiring a real
 * {@link SessionSubjectResolver} around a mocked {@link SessionRepository}: the port this adapter answers is keyed by
 * destination id from {@link #statusFor} down, so nothing below {@link #destinationsFor} touches a subject again, and
 * the resolver's own parsing rule is already covered by {@code SessionSubjectResolverTest} and
 * {@code MessageSubjectPolicyAdapterTest}.
 */
@MockitoSettings
class ChatDestinationsAdapterTest {

    private static final String ORG = "org-1";
    private static final String SUBJECT = "41";
    private static final long COMMUNITY_ID = 3L;
    private static final Instant NOW = Instant.parse("2026-09-01T16:00:00Z");

    @Mock
    private SessionSubjectResolver sessionSubjectResolver;

    @Mock
    private CommunityAccess communityAccess;

    private ChatDestinationsAdapter adapter;

    @BeforeEach
    void setUp() {

        adapter = new ChatDestinationsAdapter(sessionSubjectResolver, communityAccess);
    }

    @Test
    void channelTypesAreTheTwoMemberFacingSessionMessages() {

        assertThat(adapter.channelTypes()).containsExactlyInAnyOrder(MessageType.SESSION_UPCOMING,
                MessageType.SESSION_WRAP_UP);
    }

    @Test
    void resolvesASubjectToItsCommunitysDestinationIds() {

        when(sessionSubjectResolver.communityIdFor(ORG, SUBJECT)).thenReturn(Optional.of(COMMUNITY_ID));
        when(communityAccess.chatDestinationIds(ORG, COMMUNITY_ID)).thenReturn(List.of(7L, 9L));

        assertThat(adapter.destinationsFor(ORG, SUBJECT)).containsExactly(7L, 9L);
    }

    @Test
    void aSubjectThatResolvesToNothingHasNoDestinations() {

        when(sessionSubjectResolver.communityIdFor(ORG, SUBJECT)).thenReturn(Optional.empty());

        assertThat(adapter.destinationsFor(ORG, SUBJECT)).isEmpty();
    }

    @Test
    void aCommunityDeletedUnderneathTheDrainHasNoDestinationsRatherThanThrowing() {

        when(sessionSubjectResolver.communityIdFor(ORG, SUBJECT)).thenReturn(Optional.of(COMMUNITY_ID));
        when(communityAccess.chatDestinationIds(ORG, COMMUNITY_ID)).thenThrow(new NotFoundException("gone"));

        assertThat(adapter.destinationsFor(ORG, SUBJECT)).isEmpty();
    }

    @Test
    void statusAndHealthNoLongerNeedASubjectAtAll() {

        when(communityAccess.chatDestinationStatus(ORG, 7L)).thenReturn(new ChatChannelStatus.Broken());

        assertThat(adapter.statusFor(ORG, 7L)).isInstanceOf(ChatChannelStatus.Broken.class);

        adapter.recordDelivered(ORG, 7L, NOW);

        verify(communityAccess).recordChatDelivered(ORG, 7L, NOW);
        verifyNoInteractions(sessionSubjectResolver);
    }

    /**
     * {@link #statusAndHealthNoLongerNeedASubjectAtAll} covers {@link ChatDestinationsAdapter#statusFor} and
     * {@link ChatDestinationsAdapter#recordDelivered}; {@code recordFailure} is the third destination-keyed delegation
     * and needs its own line under coverage, including the {@code BROKEN_AFTER} threshold it reaches with.
     */
    @Test
    void recordFailureReachesTheCommunityWithTheBrokenAfterThreshold() {

        var failure = new ChatFailure(ChatFailureKind.TRANSIENT, "503", NOW);

        adapter.recordFailure(ORG, 7L, failure);

        verify(communityAccess).recordChatFailure(ORG, 7L, failure, 3);
    }
}
