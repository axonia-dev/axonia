package dev.axonia.communication.application;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.axonia.communication.api.MailServerState;
import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.api.OutboxStatus;
import dev.axonia.communication.api.RecordingMailServer;
import dev.axonia.communication.api.SuppressionReason;
import dev.axonia.communication.api.spi.ChatDestinationsPort;
import dev.axonia.communication.api.spi.MessageSubjectPolicy;
import dev.axonia.communication.application.port.out.NotificationPreferenceStore;
import dev.axonia.communication.domain.DeliveryChannel;
import dev.axonia.communication.domain.OutboxDelivery;
import dev.axonia.communication.domain.StoredPreference;
import dev.axonia.organization.api.Organization;
import dev.axonia.organization.api.Organizations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link DeliveryGate#admit} for an {@link DeliveryChannel#IN_APP} row: it keeps only the two layers that ask whether
 * the message should exist for this person at all - the subject's own type switch and whether the recipient is still a
 * member - and skips the organization kill switch and the member's own preference, since neither answers a question a
 * record inside the product asks. {@link #theSameMembersMailRowIsStillSuppressed} proves the mail track is unchanged
 * for the very same member and message: the two channels diverge, rather than the gate being loosened for everyone.
 */
class DeliveryGateInAppTest {

    private static final String ORGANIZATION_ID = "acme";
    private static final String SUBJECT_REF = "42";
    private static final String USER_ID = "alice";

    private Organizations organizations;
    private MessageSubjectPolicy policy;
    private ChatDestinationsPort chatDestinations;
    private NotificationPreferenceStore notificationPreferenceStore;
    private final RecordingMailServer mailServer = RecordingMailServer.configured(RecordingMailServer.LOCAL);
    private DeliveryGate gate;

    @BeforeEach
    void setUp() {
        organizations = mock(Organizations.class);
        policy = mock(MessageSubjectPolicy.class);
        chatDestinations = mock(ChatDestinationsPort.class);
        notificationPreferenceStore = mock(NotificationPreferenceStore.class);

        organizationHasCommunicationsEnabled(true);
        when(policy.handles()).thenReturn(Set.copyOf(MessageType.open()));
        when(policy.isEnabledFor(anyString(), any(), anyString())).thenReturn(true);
        when(policy.isStillAMember(anyString(), anyString(), anyString())).thenReturn(true);

        gate = new DeliveryGate(mailServer, organizations, new MessageSubjectPolicies(List.of(policy)),
                chatDestinations, notificationPreferenceStore);
    }

    private void organizationHasCommunicationsEnabled(boolean enabled) {
        var organization = mock(Organization.class);
        when(organization.communicationsEnabled()).thenReturn(enabled);
        when(organizations.byId(ORGANIZATION_ID)).thenReturn(Optional.of(organization));
    }

    private void subjectHasTypeEnabled(boolean enabled) {
        when(policy.isEnabledFor(ORGANIZATION_ID, MessageType.SESSION_WRAP_UP, SUBJECT_REF)).thenReturn(enabled);
    }

    private void recipientIsStillAMember(boolean stillAMember) {
        when(policy.isStillAMember(ORGANIZATION_ID, SUBJECT_REF, USER_ID)).thenReturn(stillAMember);
    }

    private void memberHasOptedOut(String userId, MessageType type) {
        when(notificationPreferenceStore.storedFor(ORGANIZATION_ID, userId, type))
                .thenReturn(Optional.of(new StoredPreference(type.code(), false)));
    }

    private OutboxDelivery inAppRow() {

        return OutboxDelivery.builder().id(1L).organizationId(ORGANIZATION_ID).messageType("session_wrap_up")
                .subjectRef(SUBJECT_REF).userId(USER_ID).channel(DeliveryChannel.IN_APP.name())
                .status(OutboxStatus.PENDING.name()).build();
    }

    @Test
    void anInAppRowIsAdmittedWhenTheOrganizationHasCommunicationsSwitchedOff() {

        organizationHasCommunicationsEnabled(false);

        assertThat(gate.admit(inAppRow())).isEmpty();
    }

    @Test
    void anInAppRowIsAdmittedForAMemberWhoOptedOutOfThatMessageType() {

        memberHasOptedOut(USER_ID, MessageType.SESSION_WRAP_UP);

        assertThat(gate.admit(inAppRow())).isEmpty();
    }

    @Test
    void anInAppRowIsSuppressedWhenTheSubjectHasTheTypeSwitchedOff() {

        subjectHasTypeEnabled(false);

        assertThat(gate.admit(inAppRow())).contains(SuppressionReason.SUBJECT_OFF);
    }

    @Test
    void anInAppRowIsSuppressedForSomebodyWhoHasLeftTheSubject() {

        recipientIsStillAMember(false);

        assertThat(gate.admit(inAppRow())).contains(SuppressionReason.NOT_A_MEMBER);
    }

    @Test
    void theSameMembersMailRowIsStillSuppressed() {

        memberHasOptedOut(USER_ID, MessageType.SESSION_WRAP_UP);

        var mailRow = OutboxDelivery.builder().id(2L).organizationId(ORGANIZATION_ID).messageType("session_wrap_up")
                .subjectRef(SUBJECT_REF).userId(USER_ID).channel(DeliveryChannel.EMAIL.name())
                .status(OutboxStatus.PENDING.name()).build();

        assertThat(gate.admit(mailRow)).contains(SuppressionReason.MEMBER_OPTED_OUT);
    }

    @Test
    void anInAppEntryIsWrittenOnAnInstallationWithNoMailServer() {

        mailServer.answer(MailServerState.notConfigured());

        assertThat(gate.admit(inAppRow())).isEmpty();
    }
}
