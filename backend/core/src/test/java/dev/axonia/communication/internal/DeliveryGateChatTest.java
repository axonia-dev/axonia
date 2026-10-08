package dev.axonia.communication.internal;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.axonia.communication.ChatChannelStatus;
import dev.axonia.communication.ChatDestinationsPort;
import dev.axonia.communication.MailServerState;
import dev.axonia.communication.MessageSubjectPolicy;
import dev.axonia.communication.MessageType;
import dev.axonia.organization.Organization;
import dev.axonia.organization.Organizations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link DeliveryGate#admitChat} judged on the row's own {@code chatDestinationId}: {@code null} for the placeholder
 * every chat-eligible message is enqueued with, non-null once {@code OutboxRowProcessor} has expanded it into one row
 * per destination.
 */
class DeliveryGateChatTest {

    private static final String ORG = "org-1";
    private static final String SUBJECT = "41";
    private static final String URL = "https://hooks.slack.com/services/x";

    private Organizations organizations;
    private MessageSubjectPolicy policy;
    private ChatDestinationsPort chatDestinations;
    private NotificationPreferenceRepository notificationPreferenceRepository;
    private final RecordingMailServer mailServer = RecordingMailServer.configured(RecordingMailServer.LOCAL);
    private DeliveryGate gate;

    @BeforeEach
    void setUp() {
        organizations = mock(Organizations.class);
        policy = mock(MessageSubjectPolicy.class);
        chatDestinations = mock(ChatDestinationsPort.class);
        notificationPreferenceRepository = mock(NotificationPreferenceRepository.class);

        var organization = mock(Organization.class);
        when(organization.communicationsEnabled()).thenReturn(true);
        when(organizations.byId(ORG)).thenReturn(Optional.of(organization));
        when(policy.handles()).thenReturn(Set.copyOf(MessageType.open()));
        when(policy.isEnabledFor(anyString(), any(), anyString())).thenReturn(true);

        gate = new DeliveryGate(mailServer, organizations, new MessageSubjectPolicies(List.of(policy)),
                chatDestinations, notificationPreferenceRepository);
    }

    private static OutboxEntity chatRow(Long chatDestinationId) {
        return OutboxEntity.builder().id(1L).organizationId(ORG).messageType(MessageType.SESSION_WRAP_UP.code())
                .subjectRef(SUBJECT).channel("CHAT").chatDestinationId(chatDestinationId).build();
    }

    @Test
    void aPlaceholderWithNoDestinationsIsSuppressedAsNoDestination() {

        when(chatDestinations.destinationsFor(ORG, SUBJECT)).thenReturn(List.of());

        assertThat(gate.admit(chatRow(null))).contains(SuppressionReason.NO_DESTINATION);
    }

    @Test
    void aPlaceholderWithDestinationsIsAdmittedSoItCanExpand() {

        when(chatDestinations.destinationsFor(ORG, SUBJECT)).thenReturn(List.of(7L));

        assertThat(gate.admit(chatRow(null))).isEmpty();
    }

    @Test
    void anExpandedRowForABrokenDestinationIsSuppressed() {

        when(chatDestinations.statusFor(ORG, 7L)).thenReturn(new ChatChannelStatus.Broken());

        assertThat(gate.admit(chatRow(7L))).contains(SuppressionReason.DESTINATION_BROKEN);
    }

    @Test
    void anExpandedRowForADestinationRemovedSinceExpansionIsSuppressedAsNoDestination() {

        when(chatDestinations.statusFor(ORG, 7L)).thenReturn(new ChatChannelStatus.NotConfigured());

        assertThat(gate.admit(chatRow(7L))).contains(SuppressionReason.NO_DESTINATION);
    }

    @Test
    void aChatRowStillSkipsBothMemberChecks() {

        when(chatDestinations.statusFor(ORG, 7L)).thenReturn(new ChatChannelStatus.Ready(URI.create(URL)));

        assertThat(gate.admit(chatRow(7L))).isEmpty();
        verifyNoInteractions(notificationPreferenceRepository);
    }

    @Test
    void theOrganizationKillSwitchStillStopsAChatRow() {

        var off = mock(Organization.class);
        when(off.communicationsEnabled()).thenReturn(false);
        when(organizations.byId(ORG)).thenReturn(Optional.of(off));

        assertThat(gate.admit(chatRow(null))).contains(SuppressionReason.ORGANIZATION_OFF);
    }

    @Test
    void theCommunitysOwnPerTypeSwitchStillStopsAChatRow() {

        when(policy.isEnabledFor(ORG, MessageType.SESSION_WRAP_UP, SUBJECT)).thenReturn(false);

        assertThat(gate.admit(chatRow(null))).contains(SuppressionReason.SUBJECT_OFF);
    }

    @Test
    void aPostGoesOutOnAnInstallationWithNoMailServer() {

        mailServer.answer(MailServerState.notConfigured());
        when(chatDestinations.destinationsFor(ORG, SUBJECT)).thenReturn(List.of(7L));

        assertThat(gate.admit(chatRow(null))).isEmpty();
    }
}
