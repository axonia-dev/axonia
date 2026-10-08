package dev.axonia.events.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.OrganizationResolver;
import dev.axonia.PublicUrlProperties;
import dev.axonia.communication.CommunicationPort;
import dev.axonia.communication.MessageType;
import dev.axonia.communication.OutboundMessage;
import dev.axonia.communication.ProductionWatermark;
import dev.axonia.community.CommunityIdentity;
import dev.axonia.events.CommunityAccess;
import dev.axonia.events.SessionResourceAccess;
import dev.axonia.events.SessionResources;
import dev.axonia.events.UserProfileAccess;
import dev.axonia.events.mother.SessionEntityMother;
import dev.axonia.events.mother.SessionMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class UpcomingSessionProducerTest {

    private static final Instant NOW = Instant.parse("2026-06-08T00:00:00Z");
    private static final String ORGANIZATION_ID = "acme";
    private static final Long COMMUNITY_ID = 7L;
    private static final String PRODUCER_KEY = UpcomingSessionProducer.PRODUCER_KEY;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private CommunityAccess communityAccess;

    @Mock
    private SessionFactsAssembler factsAssembler;

    @Mock
    private CommunicationPort port;

    @Mock
    private OrganizationResolver organizationResolver;

    @Mock
    private ProductionWatermark watermark;

    @InjectMocks
    private UpcomingSessionProducer producer;

    @BeforeEach
    void seedOrganization() {

        when(organizationResolver.currentOrganization()).thenReturn(Optional.of(ORGANIZATION_ID));
        // Default: no stored watermark yet, so since() falls back to the floor the producer passed - Instant.EPOCH,
        // this producer's own first-run backstop (it has no configured max-age, unlike WrapUpProducer's). Lenient:
        // the one test that stubs organizationResolver to fail never reaches this call.
        lenient().when(watermark.since(PRODUCER_KEY, Instant.EPOCH)).thenReturn(Instant.EPOCH);
    }

    @Test
    void aSessionPastItsReleaseIntentIsEnqueuedToEveryMember() {

        // given - which release intents are due is SessionRepository#findUpcomingNoticeSessionIds' own concern, proven
        // separately (and against a real database) by SessionRepositoryTest; here the query simply says "due".
        givenDueSession(42L, NOW.plus(2, ChronoUnit.DAYS));
        givenMembers("user-1", "user-2");

        // when
        producer.produce(NOW);

        // then
        var message = captureEnqueued();
        assertThat(message.type()).isEqualTo(MessageType.SESSION_UPCOMING);
        assertThat(message.subjectRef()).isEqualTo("42");
        assertThat(message.recipientUserIds()).containsExactlyInAnyOrder("user-1", "user-2");
        assertThat(message.template()).isEqualTo("session-upcoming");
    }

    @Test
    void aSessionWhoseReleaseIntentIsNotDueWaits() {

        // given - an intent still ahead, or absent entirely, is excluded at the SQL level: nothing comes back due.
        when(sessionRepository.findUpcomingNoticeSessionIds(ORGANIZATION_ID, NOW, Instant.EPOCH)).thenReturn(List.of());

        // when
        producer.produce(NOW);

        // then
        verifyNoInteractions(port, communityAccess, factsAssembler);
    }

    /**
     * The empty-recipients check runs before fact assembly, not after: a community with no members currently costs
     * nothing beyond the membership lookup itself - no {@link SessionFactsAssembler} call, no {@code enqueue}.
     */
    @Test
    void noEnqueueOrFactAssemblyWhenTheCommunityHasNoMembers() {

        // given
        when(sessionRepository.findUpcomingNoticeSessionIds(ORGANIZATION_ID, NOW, Instant.EPOCH))
                .thenReturn(List.of(42L));
        when(sessionRepository.findByOrganizationIdAndId(ORGANIZATION_ID, 42L))
                .thenReturn(Optional.of(SessionEntityMother.any().id(42L).communityId(COMMUNITY_ID).build()));
        when(communityAccess.memberUserIds(ORGANIZATION_ID, COMMUNITY_ID)).thenReturn(List.of());

        // when
        producer.produce(NOW);

        // then
        verifyNoInteractions(port, factsAssembler);
    }

    @Test
    void produceRefusesToRunOutsideAnOrganizationScope() {

        // given
        when(organizationResolver.currentOrganization()).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> producer.produce(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aSessionWhoseDueInstantIsBeforeTheWatermarkIsNotProducedAgain() {

        // given - a stored watermark already sits ahead of EPOCH, simulating an earlier tick's advance.
        var storedWatermark = NOW.minus(Duration.ofMinutes(5));
        when(watermark.since(PRODUCER_KEY, Instant.EPOCH)).thenReturn(storedWatermark);
        when(sessionRepository.findUpcomingNoticeSessionIds(ORGANIZATION_ID, NOW, storedWatermark))
                .thenReturn(List.of());

        // when
        producer.produce(NOW);

        // then
        verifyNoInteractions(port, communityAccess, factsAssembler);
    }

    @Test
    void theWatermarkAdvancesAfterASuccessfulProduce() {

        // given
        when(sessionRepository.findUpcomingNoticeSessionIds(ORGANIZATION_ID, NOW, Instant.EPOCH)).thenReturn(List.of());

        // when
        producer.produce(NOW);

        // then
        verify(watermark).advanceTo(PRODUCER_KEY, NOW);
    }

    @Test
    void theWatermarkDoesNotAdvanceWhenProduceFails() {

        // given
        when(sessionRepository.findUpcomingNoticeSessionIds(ORGANIZATION_ID, NOW, Instant.EPOCH))
                .thenThrow(new IllegalStateException("boom"));

        // when / then
        assertThatThrownBy(() -> producer.produce(NOW)).isInstanceOf(IllegalStateException.class);
        verify(watermark, never()).advanceTo(any(), any());
    }

    /**
     * "Prove the loop actually closes." Every other test here mocks {@link SessionFactsAssembler}; this one wires a
     * REAL one (with mocked leaf collaborators) into a locally-built producer, driving the actual
     * {@code SessionFacts.of} call the other tests never reach. Asserts the enqueued facts carry the real
     * {@code /communities/{slug}/sessions/{id}} route (never the retired {@code /s/{id}}) and a resolved presenter name
     * - never the raw {@code "dev-user"} id {@link SessionMother}'s default slot carries.
     */
    @Test
    void theEnqueuedFactsCarryARealSessionUrlAndAResolvedPresenterName() {

        // given
        var sessionMapper = mock(SessionMapper.class);
        var seriesRepository = mock(SeriesRepository.class);
        var loopSessionRepository = mock(SessionRepository.class);
        var loopCommunityAccess = mock(CommunityAccess.class);
        var userProfileAccess = mock(UserProfileAccess.class);
        var sessionResourceAccess = mock(SessionResourceAccess.class);
        var actionItemRepository = mock(ActionItemRepository.class);
        var actionItemCheckpointRepository = mock(ActionItemCheckpointRepository.class);
        var publicUrlProperties = new PublicUrlProperties("https://axonia.example");

        var entity = SessionEntityMother.any().id(42L).communityId(COMMUNITY_ID).seriesId(null).build();
        var session = SessionMother.any().id(42L).seriesId(null).build();

        when(loopSessionRepository.findUpcomingNoticeSessionIds(ORGANIZATION_ID, NOW, Instant.EPOCH))
                .thenReturn(List.of(42L));
        when(loopSessionRepository.findByOrganizationIdAndId(ORGANIZATION_ID, 42L)).thenReturn(Optional.of(entity));
        when(sessionMapper.toSession(entity)).thenReturn(session);
        when(loopCommunityAccess.communityIdentity(ORGANIZATION_ID, COMMUNITY_ID))
                .thenReturn(new CommunityIdentity("frontend-guild", "Frontend Guild", "#1aa6a0"));
        when(loopCommunityAccess.memberUserIds(ORGANIZATION_ID, COMMUNITY_ID)).thenReturn(List.of("user-1"));
        when(userProfileAccess.displayNames(ORGANIZATION_ID, List.of("dev-user")))
                .thenReturn(Map.of("dev-user", "Ada Lovelace"));
        when(sessionResourceAccess.resourcesForSession(ORGANIZATION_ID, 42L, COMMUNITY_ID))
                .thenReturn(new SessionResources(List.of(), Map.of()));

        var realAssembler = new SessionFactsAssembler(sessionMapper, seriesRepository, loopSessionRepository,
                loopCommunityAccess, userProfileAccess, sessionResourceAccess, actionItemRepository,
                actionItemCheckpointRepository, publicUrlProperties);
        var loopProducer = new UpcomingSessionProducer(loopSessionRepository, loopCommunityAccess, realAssembler, port,
                organizationResolver, watermark);

        // when
        loopProducer.produce(NOW);

        // then
        var captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(port).enqueue(captor.capture());
        var facts = captor.getValue().facts();
        assertThat(facts).containsEntry("sessionUrl", "https://axonia.example/communities/frontend-guild/sessions/42");
        var talks = (List<?>) facts.get("talks");
        assertThat(talks).singleElement()
                .satisfies(talk -> assertThat(talk).hasFieldOrPropertyWithValue("presenter", "Ada Lovelace"));
        assertThat(facts.toString()).doesNotContain("dev-user");
    }

    private void givenDueSession(long sessionId, Instant scheduledAt) {

        when(sessionRepository.findUpcomingNoticeSessionIds(ORGANIZATION_ID, NOW, Instant.EPOCH))
                .thenReturn(List.of(sessionId));
        when(sessionRepository.findByOrganizationIdAndId(ORGANIZATION_ID, sessionId)).thenReturn(Optional.of(
                SessionEntityMother.any().id(sessionId).communityId(COMMUNITY_ID).scheduledAt(scheduledAt).build()));
    }

    private void givenMembers(String... userIds) {

        when(communityAccess.memberUserIds(ORGANIZATION_ID, COMMUNITY_ID)).thenReturn(List.of(userIds));
    }

    private OutboundMessage captureEnqueued() {

        var captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(port).enqueue(captor.capture());
        return captor.getValue();
    }
}
