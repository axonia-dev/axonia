package dev.axonia.community;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.dao.DataIntegrityViolationException;

import dev.axonia.NotFoundException;
import dev.axonia.ValidationException;
import dev.axonia.communication.ChatChannelProbe;
import dev.axonia.communication.ChatChannelStatus;
import dev.axonia.communication.ChatDestinationsPort.ChatFailure;
import dev.axonia.communication.ChatFailureKind;
import dev.axonia.communication.ChatPlatformName;
import dev.axonia.communication.ChatSendFailedException;
import dev.axonia.communication.ChatWebhooks;
import dev.axonia.community.internal.ChatDestinationEntity;
import dev.axonia.community.internal.ChatDestinationRepository;
import dev.axonia.community.internal.ChatHealthRecorder;
import dev.axonia.community.internal.Communities;
import dev.axonia.community.internal.CommunityEntity;
import dev.axonia.community.internal.MembershipAuthorizer;
import dev.axonia.community.mother.CommunityEntityMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class ChatDestinationServiceTest {

    private static final String ORG = "default";
    private static final String SLUG = "backend-guild";
    private static final Long COMMUNITY_ID = 1L;
    private static final long DESTINATION_ID = 7L;
    private static final String URL = "https://hooks.slack.com/services/x";
    private static final String NEW_URL = "https://hooks.slack.com/services/y";
    private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");

    @Mock
    private ChatDestinationRepository chatDestinationRepository;

    @Mock
    private Communities communities;

    @Mock
    private MembershipAuthorizer membershipAuthorizer;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private OrganizationContext organizationContext;

    @Mock
    private ChatWebhooks chatWebhooks;

    @Mock
    private ChatChannelProbe chatChannelProbe;

    @Mock
    private ChatHealthRecorder chatHealthRecorder;

    @Mock
    private Clock clock;

    @InjectMocks
    private ChatDestinationService service;

    private CommunityEntity community;

    @BeforeEach
    void seedSeams() {

        lenient().when(organizationContext.organizationId()).thenReturn(ORG);
        lenient().when(currentUser.userId()).thenReturn("dev-user");
        lenient().when(clock.instant()).thenReturn(NOW);
        community = CommunityEntityMother.any().id(COMMUNITY_ID).build();
    }

    private ChatDestinationEntity entity(String name, String url) {

        return ChatDestinationEntity.builder().id(DESTINATION_ID).organizationId(ORG).communityId(COMMUNITY_ID)
                .name(name).webhookUrl(url).build();
    }

    @Test
    void rejectsAUrlThatBelongsToNoKnownPlatform() {

        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(chatWebhooks.platformOf("https://evil.example/hook")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(SLUG,
                new CreateChatDestinationRequest("#guild", ChatPlatformName.SLACK, "https://evil.example/hook")))
                .isInstanceOf(ValidationException.class).hasMessageContaining("hooks.slack.com");
    }

    /**
     * The declared platform narrows the allowlist rather than replacing it, so a URL a different platform owns is
     * refused as firmly as one no platform owns - and the refusal names the shape the leader said they were pasting,
     * which is the whole reason the leader declares it.
     */
    @Test
    void rejectsAUrlBelongingToAPlatformOtherThanTheDeclaredOne() {

        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(chatWebhooks.platformOf(URL)).thenReturn(Optional.of(ChatPlatformName.SLACK));

        assertThatThrownBy(
                () -> service.create(SLUG, new CreateChatDestinationRequest("#guild", ChatPlatformName.TEAMS, URL)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("Power Automate");
        verify(chatDestinationRepository, never()).save(any());
    }

    /** A replacement URL without a platform beside it cannot be checked against one, so it is refused, not guessed. */
    @Test
    void rejectsAReplacementUrlWithNoDeclaredPlatform() {

        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(chatDestinationRepository.findByOrganizationIdAndId(ORG, DESTINATION_ID))
                .thenReturn(Optional.of(entity("#guild", URL)));

        assertThatThrownBy(
                () -> service.update(SLUG, DESTINATION_ID, new UpdateChatDestinationRequest("#guild", null, NEW_URL)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("which chat tool");
        verify(chatDestinationRepository, never()).save(any());
    }

    @Test
    void refusesAnEleventhDestination() {

        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(chatWebhooks.platformOf(URL)).thenReturn(Optional.of(ChatPlatformName.SLACK));
        when(chatDestinationRepository.countByOrganizationIdAndCommunityId(ORG, COMMUNITY_ID)).thenReturn(10L);

        assertThatThrownBy(
                () -> service.create(SLUG, new CreateChatDestinationRequest("#eleventh", ChatPlatformName.SLACK, URL)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("ten");
    }

    @Test
    void refusesASecondDestinationWithTheSameName() {

        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(chatWebhooks.platformOf(URL)).thenReturn(Optional.of(ChatPlatformName.SLACK));
        when(chatDestinationRepository.existsByOrganizationIdAndCommunityIdAndName(ORG, COMMUNITY_ID, "#guild"))
                .thenReturn(true);

        assertThatThrownBy(
                () -> service.create(SLUG, new CreateChatDestinationRequest("#guild", ChatPlatformName.SLACK, URL)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("already has a destination");
    }

    /**
     * The race {@link #refusesASecondDestinationWithTheSameName} cannot reach: two concurrent creates for the same name
     * both pass {@code requireNameFree}'s read, and the loser hits the database's own
     * {@code community_chat_destination_named_once} constraint instead. This is that loser's path, simulated by having
     * {@code requireNameFree}'s own check pass (the mock's default {@code existsBy...} is {@code false}) and the save
     * itself fail the way Postgres actually reports a unique violation.
     */
    @Test
    void refusesASecondDestinationWithTheSameNameEvenWhenTheRaceReachesTheDatabaseInstead() {

        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(chatWebhooks.platformOf(URL)).thenReturn(Optional.of(ChatPlatformName.SLACK));
        when(chatDestinationRepository.save(any())).thenThrow(new DataIntegrityViolationException(
                "ERROR: duplicate key value violates unique constraint \"community_chat_destination_named_once\""));

        assertThatThrownBy(
                () -> service.create(SLUG, new CreateChatDestinationRequest("#guild", ChatPlatformName.SLACK, URL)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("already has a destination");
    }

    /**
     * The narrowing {@link #save}'s own javadoc promises: a {@link DataIntegrityViolationException} from some other
     * constraint - or a genuine outage - is not the name race and must not be misreported as one.
     */
    @Test
    void aSaveFailureUnrelatedToTheNameConstraintIsNotMisreportedAsANameCollision() {

        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(chatWebhooks.platformOf(URL)).thenReturn(Optional.of(ChatPlatformName.SLACK));
        when(chatDestinationRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("connection to server was lost"));

        assertThatThrownBy(
                () -> service.create(SLUG, new CreateChatDestinationRequest("#guild", ChatPlatformName.SLACK, URL)))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("connection to server");
    }

    @Test
    void createsANewDestination() {

        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(chatWebhooks.platformOf(URL)).thenReturn(Optional.of(ChatPlatformName.SLACK));
        when(chatDestinationRepository.save(any())).thenAnswer(call -> {
            ChatDestinationEntity toSave = call.getArgument(0);
            return toSave.toBuilder().id(DESTINATION_ID).build();
        });

        var created = service.create(SLUG, new CreateChatDestinationRequest(" #guild ", ChatPlatformName.SLACK, URL));

        var saved = ArgumentCaptor.forClass(ChatDestinationEntity.class);
        verify(chatDestinationRepository).save(saved.capture());
        assertThat(saved.getValue().getOrganizationId()).isEqualTo(ORG);
        assertThat(saved.getValue().getCommunityId()).isEqualTo(COMMUNITY_ID);
        assertThat(saved.getValue().getName()).isEqualTo("#guild");
        assertThat(saved.getValue().getWebhookUrl()).isEqualTo(URL);
        assertThat(created.id()).isEqualTo(DESTINATION_ID);
        assertThat(created.name()).isEqualTo("#guild");
        assertThat(created.platform()).isEqualTo(ChatPlatformName.SLACK);
    }

    @Test
    void neverReturnsTheStoredUrl() {

        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(chatDestinationRepository.findByOrganizationIdAndCommunityIdOrderByIdAsc(ORG, COMMUNITY_ID))
                .thenReturn(List.of(entity("#guild", "https://hooks.slack.com/services/T/B/abcd")));
        when(chatWebhooks.platformOf(anyString())).thenReturn(Optional.of(ChatPlatformName.SLACK));

        assertThat(service.list(SLUG)).singleElement().satisfies(d -> {
            assertThat(d.maskedUrl()).isEqualTo("hooks.slack.com/...abcd");
            assertThat(d.name()).isEqualTo("#guild");
        });
    }

    @Test
    void updatingWithAnEmptyUrlKeepsTheStoredOneAndOnlyRenames() {

        var stored = entity("#guild", URL);
        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(chatDestinationRepository.findByOrganizationIdAndId(ORG, DESTINATION_ID)).thenReturn(Optional.of(stored));
        when(chatWebhooks.platformOf(URL)).thenReturn(Optional.of(ChatPlatformName.SLACK));
        when(chatDestinationRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.update(SLUG, DESTINATION_ID, new UpdateChatDestinationRequest("#renamed", null, null));

        var saved = ArgumentCaptor.forClass(ChatDestinationEntity.class);
        verify(chatDestinationRepository).save(saved.capture());
        assertThat(saved.getValue().getWebhookUrl()).isEqualTo(URL);
        assertThat(saved.getValue().getName()).isEqualTo("#renamed");
    }

    @Test
    void savingANewUrlClearsTheBrokenState() {

        var stored = entity("#guild", URL).toBuilder().broken(true).consecutiveFailures(3).lastError("410 Gone")
                .build();
        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(chatDestinationRepository.findByOrganizationIdAndId(ORG, DESTINATION_ID)).thenReturn(Optional.of(stored));
        when(chatWebhooks.platformOf(NEW_URL)).thenReturn(Optional.of(ChatPlatformName.SLACK));
        when(chatDestinationRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.update(SLUG, DESTINATION_ID,
                new UpdateChatDestinationRequest("#guild", ChatPlatformName.SLACK, NEW_URL));

        var saved = ArgumentCaptor.forClass(ChatDestinationEntity.class);
        verify(chatDestinationRepository).save(saved.capture());
        assertThat(saved.getValue().isBroken()).isFalse();
        assertThat(saved.getValue().getConsecutiveFailures()).isZero();
        assertThat(saved.getValue().getLastError()).isNull();
    }

    @Test
    void removesTheDestination() {

        var stored = entity("#guild", URL);
        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(chatDestinationRepository.findByOrganizationIdAndId(ORG, DESTINATION_ID)).thenReturn(Optional.of(stored));

        service.remove(SLUG, DESTINATION_ID);

        verify(chatDestinationRepository).delete(stored);
    }

    @Test
    void removeOfAnUnknownDestinationIsNotFound() {

        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(chatDestinationRepository.findByOrganizationIdAndId(ORG, DESTINATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(SLUG, DESTINATION_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void test_sendsThroughTheStoredDestinationAndRecordsSuccess() {

        var stored = entity("#guild", URL);
        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(chatDestinationRepository.findByOrganizationIdAndId(ORG, DESTINATION_ID)).thenReturn(Optional.of(stored));

        service.test(SLUG, DESTINATION_ID);

        verify(chatChannelProbe).postTest(URL, community.getName());
        verify(chatHealthRecorder).recordDelivered(ORG, DESTINATION_ID, NOW);
    }

    @Test
    void test_onAFailureRecordsItAndRethrows() {

        var stored = entity("#guild", URL);
        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(chatDestinationRepository.findByOrganizationIdAndId(ORG, DESTINATION_ID)).thenReturn(Optional.of(stored));
        doThrow(new ChatSendFailedException(ChatFailureKind.GONE, "404 from hooks.slack.com")).when(chatChannelProbe)
                .postTest(anyString(), anyString());

        assertThatThrownBy(() -> service.test(SLUG, DESTINATION_ID)).isInstanceOf(ChatSendFailedException.class);

        verify(chatHealthRecorder).recordFailure(ORG, DESTINATION_ID,
                new ChatFailure(ChatFailureKind.GONE, "404 from hooks.slack.com", NOW), 3);
    }

    @Test
    void test_rejectsAStoredUrlThatNoLongerParses() {

        var stored = entity("#guild", "https://bad host.example/hook-legacy");
        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(chatDestinationRepository.findByOrganizationIdAndId(ORG, DESTINATION_ID)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.test(SLUG, DESTINATION_ID)).isInstanceOf(ValidationException.class);
        verifyNoInteractions(chatChannelProbe);
    }

    @Test
    void test_aBookkeepingFailureOnSuccessDoesNotFailTheRequest() {

        var stored = entity("#guild", URL);
        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(chatDestinationRepository.findByOrganizationIdAndId(ORG, DESTINATION_ID)).thenReturn(Optional.of(stored));
        doThrow(new DataIntegrityViolationException("boom")).when(chatHealthRecorder).recordDelivered(ORG,
                DESTINATION_ID, NOW);

        assertThatCode(() -> service.test(SLUG, DESTINATION_ID)).doesNotThrowAnyException();

        verify(chatChannelProbe).postTest(URL, community.getName());
    }

    @Test
    void statusFor_notConfiguredWhenNoDestinationIsStored() {

        when(chatDestinationRepository.findByOrganizationIdAndId(ORG, DESTINATION_ID)).thenReturn(Optional.empty());

        assertThat(service.statusFor(ORG, DESTINATION_ID)).isInstanceOf(ChatChannelStatus.NotConfigured.class);
    }

    @Test
    void statusFor_brokenWhenTheDestinationIsMarkedBroken() {

        var stored = entity("#guild", URL).toBuilder().broken(true).build();
        when(chatDestinationRepository.findByOrganizationIdAndId(ORG, DESTINATION_ID)).thenReturn(Optional.of(stored));

        assertThat(service.statusFor(ORG, DESTINATION_ID)).isInstanceOf(ChatChannelStatus.Broken.class);
    }

    @Test
    void statusFor_readyWithTheRealUnmaskedUrlWhenNotBroken() {

        var stored = entity("#guild", URL);
        when(chatDestinationRepository.findByOrganizationIdAndId(ORG, DESTINATION_ID)).thenReturn(Optional.of(stored));

        var status = service.statusFor(ORG, DESTINATION_ID);

        assertThat(status).isInstanceOf(ChatChannelStatus.Ready.class);
        assertThat(((ChatChannelStatus.Ready) status).url()).isEqualTo(URI.create(URL));
    }

    @Test
    void statusFor_notConfiguredWhenTheStoredUrlNoLongerParses() {

        var stored = entity("#guild", "https://bad host.example/hook-legacy");
        when(chatDestinationRepository.findByOrganizationIdAndId(ORG, DESTINATION_ID)).thenReturn(Optional.of(stored));

        assertThat(service.statusFor(ORG, DESTINATION_ID)).isInstanceOf(ChatChannelStatus.NotConfigured.class);
    }

    @Test
    void destinationIdsFor_returnsTheCommunitysDestinationIds() {

        when(chatDestinationRepository.findIdsByOrganizationIdAndCommunityId(ORG, COMMUNITY_ID))
                .thenReturn(List.of(DESTINATION_ID, 8L));

        assertThat(service.destinationIdsFor(ORG, COMMUNITY_ID)).containsExactly(DESTINATION_ID, 8L);
    }

    @Test
    void recordChatDelivered_forwardsToTheChatHealthRecorder() {

        service.recordChatDelivered(ORG, DESTINATION_ID, NOW);

        verify(chatHealthRecorder).recordDelivered(ORG, DESTINATION_ID, NOW);
    }

    @Test
    void recordChatFailure_forwardsToTheChatHealthRecorderWithTheCallersBrokenAfterThreshold() {

        var failure = new ChatFailure(ChatFailureKind.TRANSIENT, "503", NOW);

        service.recordChatFailure(ORG, DESTINATION_ID, failure, 3);

        verify(chatHealthRecorder).recordFailure(ORG, DESTINATION_ID, failure, 3);
    }

}
