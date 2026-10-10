package dev.axonia.community.application;

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

import dev.axonia.communication.api.ChatChannelProbe;
import dev.axonia.communication.api.ChatChannelStatus;
import dev.axonia.communication.api.ChatFailureKind;
import dev.axonia.communication.api.ChatPlatformName;
import dev.axonia.communication.api.ChatSendFailedException;
import dev.axonia.communication.api.ChatWebhooks;
import dev.axonia.communication.api.spi.ChatDestinationsPort.ChatFailure;
import dev.axonia.community.application.port.out.ChatDestinationStore;
import dev.axonia.community.application.port.out.CurrentUser;
import dev.axonia.community.application.port.out.OrganizationContext;
import dev.axonia.community.domain.ChatDestinationRecord;
import dev.axonia.community.domain.CommunityRecord;
import dev.axonia.community.domain.CommunityRecordMother;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.ValidationException;

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
    private ChatDestinationStore destinations;

    @Mock
    private CommunityLookup communities;

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

    private CommunityRecord community;

    @BeforeEach
    void seedSeams() {

        lenient().when(organizationContext.organizationId()).thenReturn(ORG);
        lenient().when(currentUser.userId()).thenReturn("dev-user");
        lenient().when(clock.instant()).thenReturn(NOW);
        community = CommunityRecordMother.any().id(COMMUNITY_ID).build();
    }

    private ChatDestinationRecord entity(String name, String url) {

        return ChatDestinationRecord.builder().id(DESTINATION_ID).organizationId(ORG).communityId(COMMUNITY_ID)
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
        verify(destinations, never()).add(any());
    }

    /** A replacement URL without a platform beside it cannot be checked against one, so it is refused, not guessed. */
    @Test
    void rejectsAReplacementUrlWithNoDeclaredPlatform() {

        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(destinations.find(ORG, DESTINATION_ID)).thenReturn(Optional.of(entity("#guild", URL)));

        assertThatThrownBy(
                () -> service.update(SLUG, DESTINATION_ID, new UpdateChatDestinationRequest("#guild", null, NEW_URL)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("which chat tool");
        verify(destinations, never()).update(any());
    }

    @Test
    void refusesAnEleventhDestination() {

        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(chatWebhooks.platformOf(URL)).thenReturn(Optional.of(ChatPlatformName.SLACK));
        when(destinations.countOf(ORG, COMMUNITY_ID)).thenReturn(10L);

        assertThatThrownBy(
                () -> service.create(SLUG, new CreateChatDestinationRequest("#eleventh", ChatPlatformName.SLACK, URL)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("ten");
    }

    @Test
    void refusesASecondDestinationWithTheSameName() {

        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(chatWebhooks.platformOf(URL)).thenReturn(Optional.of(ChatPlatformName.SLACK));
        when(destinations.nameTaken(ORG, COMMUNITY_ID, "#guild")).thenReturn(true);

        assertThatThrownBy(
                () -> service.create(SLUG, new CreateChatDestinationRequest("#guild", ChatPlatformName.SLACK, URL)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("already has a destination");
    }

    @Test
    void createsANewDestination() {

        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(chatWebhooks.platformOf(URL)).thenReturn(Optional.of(ChatPlatformName.SLACK));
        when(destinations.add(any())).thenAnswer(call -> {
            ChatDestinationRecord toSave = call.getArgument(0);
            return toSave.toBuilder().id(DESTINATION_ID).build();
        });

        var created = service.create(SLUG, new CreateChatDestinationRequest(" #guild ", ChatPlatformName.SLACK, URL));

        var saved = ArgumentCaptor.forClass(ChatDestinationRecord.class);
        verify(destinations).add(saved.capture());
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
        when(destinations.listOf(ORG, COMMUNITY_ID))
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
        when(destinations.find(ORG, DESTINATION_ID)).thenReturn(Optional.of(stored));
        when(chatWebhooks.platformOf(URL)).thenReturn(Optional.of(ChatPlatformName.SLACK));
        when(destinations.update(any())).thenAnswer(call -> call.getArgument(0));

        service.update(SLUG, DESTINATION_ID, new UpdateChatDestinationRequest("#renamed", null, null));

        var saved = ArgumentCaptor.forClass(ChatDestinationRecord.class);
        verify(destinations).update(saved.capture());
        assertThat(saved.getValue().getWebhookUrl()).isEqualTo(URL);
        assertThat(saved.getValue().getName()).isEqualTo("#renamed");
    }

    @Test
    void savingANewUrlClearsTheBrokenState() {

        var stored = entity("#guild", URL).toBuilder().broken(true).consecutiveFailures(3).lastError("410 Gone")
                .build();
        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(destinations.find(ORG, DESTINATION_ID)).thenReturn(Optional.of(stored));
        when(chatWebhooks.platformOf(NEW_URL)).thenReturn(Optional.of(ChatPlatformName.SLACK));
        when(destinations.update(any())).thenAnswer(call -> call.getArgument(0));

        service.update(SLUG, DESTINATION_ID,
                new UpdateChatDestinationRequest("#guild", ChatPlatformName.SLACK, NEW_URL));

        var saved = ArgumentCaptor.forClass(ChatDestinationRecord.class);
        verify(destinations).update(saved.capture());
        assertThat(saved.getValue().isBroken()).isFalse();
        assertThat(saved.getValue().getConsecutiveFailures()).isZero();
        assertThat(saved.getValue().getLastError()).isNull();
    }

    @Test
    void removesTheDestination() {

        var stored = entity("#guild", URL);
        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(destinations.find(ORG, DESTINATION_ID)).thenReturn(Optional.of(stored));

        service.remove(SLUG, DESTINATION_ID);

        verify(destinations).remove(stored);
    }

    @Test
    void removeOfAnUnknownDestinationIsNotFound() {

        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(destinations.find(ORG, DESTINATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(SLUG, DESTINATION_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void test_sendsThroughTheStoredDestinationAndRecordsSuccess() {

        var stored = entity("#guild", URL);
        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(destinations.find(ORG, DESTINATION_ID)).thenReturn(Optional.of(stored));

        service.test(SLUG, DESTINATION_ID);

        verify(chatChannelProbe).postTest(URL, community.getName());
        verify(chatHealthRecorder).recordDelivered(ORG, DESTINATION_ID, NOW);
    }

    @Test
    void test_onAFailureRecordsItAndRethrows() {

        var stored = entity("#guild", URL);
        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(destinations.find(ORG, DESTINATION_ID)).thenReturn(Optional.of(stored));
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
        when(destinations.find(ORG, DESTINATION_ID)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.test(SLUG, DESTINATION_ID)).isInstanceOf(ValidationException.class);
        verifyNoInteractions(chatChannelProbe);
    }

    @Test
    void test_aBookkeepingFailureOnSuccessDoesNotFailTheRequest() {

        var stored = entity("#guild", URL);
        when(communities.findBySlugOrThrow(ORG, SLUG)).thenReturn(community);
        when(destinations.find(ORG, DESTINATION_ID)).thenReturn(Optional.of(stored));
        doThrow(new DataIntegrityViolationException("boom")).when(chatHealthRecorder).recordDelivered(ORG,
                DESTINATION_ID, NOW);

        assertThatCode(() -> service.test(SLUG, DESTINATION_ID)).doesNotThrowAnyException();

        verify(chatChannelProbe).postTest(URL, community.getName());
    }

    @Test
    void statusFor_notConfiguredWhenNoDestinationIsStored() {

        when(destinations.find(ORG, DESTINATION_ID)).thenReturn(Optional.empty());

        assertThat(service.statusFor(ORG, DESTINATION_ID)).isInstanceOf(ChatChannelStatus.NotConfigured.class);
    }

    @Test
    void statusFor_brokenWhenTheDestinationIsMarkedBroken() {

        var stored = entity("#guild", URL).toBuilder().broken(true).build();
        when(destinations.find(ORG, DESTINATION_ID)).thenReturn(Optional.of(stored));

        assertThat(service.statusFor(ORG, DESTINATION_ID)).isInstanceOf(ChatChannelStatus.Broken.class);
    }

    @Test
    void statusFor_readyWithTheRealUnmaskedUrlWhenNotBroken() {

        var stored = entity("#guild", URL);
        when(destinations.find(ORG, DESTINATION_ID)).thenReturn(Optional.of(stored));

        var status = service.statusFor(ORG, DESTINATION_ID);

        assertThat(status).isInstanceOf(ChatChannelStatus.Ready.class);
        assertThat(((ChatChannelStatus.Ready) status).url()).isEqualTo(URI.create(URL));
    }

    @Test
    void statusFor_notConfiguredWhenTheStoredUrlNoLongerParses() {

        var stored = entity("#guild", "https://bad host.example/hook-legacy");
        when(destinations.find(ORG, DESTINATION_ID)).thenReturn(Optional.of(stored));

        assertThat(service.statusFor(ORG, DESTINATION_ID)).isInstanceOf(ChatChannelStatus.NotConfigured.class);
    }

    @Test
    void destinationIdsFor_returnsTheCommunitysDestinationIds() {

        when(destinations.idsOf(ORG, COMMUNITY_ID)).thenReturn(List.of(DESTINATION_ID, 8L));

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
