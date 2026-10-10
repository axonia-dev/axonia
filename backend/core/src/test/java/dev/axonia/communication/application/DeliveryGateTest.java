package dev.axonia.communication.application;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.communication.api.MailServerState;
import dev.axonia.communication.api.MessageType;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryGateTest {

    private static final String ORGANIZATION_ID = "default";
    private static final String SUBJECT_REF = "42";
    private static final String USER_ID = "user-1";

    @Mock
    Organizations organizations;
    @Mock
    MessageSubjectPolicy messageSubjectPolicy;
    @Mock
    ChatDestinationsPort chatDestinations;
    @Mock
    NotificationPreferenceStore notificationPreferenceStore;

    private final RecordingMailServer mailServer = RecordingMailServer.configured(RecordingMailServer.LOCAL);

    DeliveryGate gate;

    @BeforeEach
    void setUp() {

        when(messageSubjectPolicy.handles()).thenReturn(Set.copyOf(MessageType.open()));
        gate = new DeliveryGate(mailServer, organizations, new MessageSubjectPolicies(List.of(messageSubjectPolicy)),
                chatDestinations, notificationPreferenceStore);
    }

    private void givenOrganization(boolean communicationsEnabled) {

        when(organizations.byId(ORGANIZATION_ID)).thenReturn(Optional
                .of(Organization.builder().id(ORGANIZATION_ID).communicationsEnabled(communicationsEnabled).build()));
    }

    private void givenSubjectAllows(MessageType type, boolean enabled) {

        when(messageSubjectPolicy.isEnabledFor(ORGANIZATION_ID, type, SUBJECT_REF)).thenReturn(enabled);
    }

    private void givenMemberPreference(MessageType type, boolean enabled) {

        when(notificationPreferenceStore.storedFor(ORGANIZATION_ID, USER_ID, type))
                .thenReturn(Optional.of(new StoredPreference(type.code(), enabled)));
    }

    private void givenStillAMember() {

        when(messageSubjectPolicy.isStillAMember(ORGANIZATION_ID, SUBJECT_REF, USER_ID)).thenReturn(true);
    }

    private void givenNoLongerAMember() {

        when(messageSubjectPolicy.isStillAMember(ORGANIZATION_ID, SUBJECT_REF, USER_ID)).thenReturn(false);
    }

    private void givenEverythingOn() {

        givenOrganization(true);
        givenSubjectAllows(MessageType.SESSION_WRAP_UP, true);
        givenMemberPreference(MessageType.SESSION_WRAP_UP, true);
        givenStillAMember();
    }

    private static OutboxDelivery aPendingRow() {
        return aPendingRow(MessageType.SESSION_WRAP_UP);
    }

    private static OutboxDelivery aPendingRow(MessageType type) {
        return aPendingRowWithCode(type.code());
    }

    private static OutboxDelivery aPendingRowWithCode(String messageTypeCode) {
        return OutboxDelivery.builder().id(1L).organizationId(ORGANIZATION_ID).messageType(messageTypeCode)
                .subjectRef(SUBJECT_REF).userId(USER_ID).template("session-wrap-up").status("PENDING").build();
    }

    /** Wider than the kill switch: with no server there is nobody to send through, whatever any setting says. */
    @Test
    void aMailRowMeetsNoMailServerBeforeAnyOtherCheck() {

        mailServer.answer(MailServerState.notConfigured());

        assertThat(gate.admit(aPendingRow())).contains(SuppressionReason.MAIL_NOT_CONFIGURED);
        verifyNoInteractions(organizations, chatDestinations, notificationPreferenceStore);
        verify(messageSubjectPolicy, never()).isEnabledFor(anyString(), any(), anyString());
    }

    @Test
    void anEmailRowNamedSoMeetsNoMailServerToo() {

        mailServer.answer(MailServerState.notConfigured());
        var row = OutboxDelivery.builder().id(1L).organizationId(ORGANIZATION_ID)
                .messageType(MessageType.SESSION_WRAP_UP.code()).subjectRef(SUBJECT_REF).userId(USER_ID)
                .channel(DeliveryChannel.EMAIL.name()).status("PENDING").build();

        assertThat(gate.admit(row)).contains(SuppressionReason.MAIL_NOT_CONFIGURED);
    }

    /** Asked for every row, never remembered: a server configured since the last row reaches the very next one. */
    @Test
    void aMailServerConfiguredSinceTheLastRowLeavesTheFourLayersToJudge() {

        mailServer.answer(MailServerState.notConfigured());
        assertThat(gate.admit(aPendingRow())).contains(SuppressionReason.MAIL_NOT_CONFIGURED);
        mailServer.answer(MailServerState.configured(RecordingMailServer.LOCAL));
        givenOrganization(false);

        assertThat(gate.admit(aPendingRow())).contains(SuppressionReason.ORGANIZATION_OFF);
    }

    @Test
    void anOrganizationWithCommunicationsOffSendsNothing() {

        givenOrganization(false);

        assertThat(gate.admit(aPendingRow())).contains(SuppressionReason.ORGANIZATION_OFF);
    }

    @Test
    void aSubjectWithWrapUpsSwitchedOffSendsNoWrapUp() {

        givenOrganization(true);
        givenSubjectAllows(MessageType.SESSION_WRAP_UP, false);

        assertThat(gate.admit(aPendingRow(MessageType.SESSION_WRAP_UP))).contains(SuppressionReason.SUBJECT_OFF);
    }

    /**
     * Layer two and layer four must reach the policy that claims the row's type. Asking whichever policy happened to be
     * injected is the fault this routing exists to prevent: a policy that cannot read a subject fails closed on it, and
     * every row of that type would be suppressed forever under a reason nothing configured.
     */
    @Test
    void aRowIsJudgedByThePolicyThatClaimsItsType() {

        var upcomingPolicy = mock(MessageSubjectPolicy.class);
        when(upcomingPolicy.handles()).thenReturn(Set.of(MessageType.SESSION_UPCOMING));
        var otherPolicy = mock(MessageSubjectPolicy.class);
        // Every remaining type, so that construction stays valid as new ones are declared: the gate refuses to be
        // built while any of them is unrouted, and that refusal is SubjectPolicyCoverageTest's subject, not this one's.
        when(otherPolicy.handles()).thenReturn(MessageType.open().stream()
                .filter(type -> !MessageType.SESSION_UPCOMING.equals(type)).collect(Collectors.toSet()));
        when(otherPolicy.isEnabledFor(ORGANIZATION_ID, MessageType.SESSION_WRAP_UP, SUBJECT_REF)).thenReturn(false);
        givenOrganization(true);

        var routed = new DeliveryGate(mailServer, organizations,
                new MessageSubjectPolicies(List.of(upcomingPolicy, otherPolicy)), chatDestinations,
                notificationPreferenceStore);

        assertThat(routed.admit(aPendingRow(MessageType.SESSION_WRAP_UP))).contains(SuppressionReason.SUBJECT_OFF);
        verify(upcomingPolicy, never()).isEnabledFor(anyString(), any(), anyString());
    }

    @Test
    void aMemberWhoUnsubscribedAfterTheRowWasWrittenIsStillHonoured() {

        givenOrganization(true);
        givenSubjectAllows(MessageType.SESSION_WRAP_UP, true);
        givenMemberPreference(MessageType.SESSION_WRAP_UP, false);

        assertThat(gate.admit(aPendingRow(MessageType.SESSION_WRAP_UP))).contains(SuppressionReason.MEMBER_OPTED_OUT);
    }

    @Test
    void someoneWhoLeftTheCommunityGetsNothing() {

        givenOrganization(true);
        givenSubjectAllows(MessageType.SESSION_WRAP_UP, true);
        givenMemberPreference(MessageType.SESSION_WRAP_UP, true);
        givenNoLongerAMember();

        assertThat(gate.admit(aPendingRow())).contains(SuppressionReason.NOT_A_MEMBER);
    }

    @Test
    void anAdmittedRowHasNoReason() {

        givenEverythingOn();

        assertThat(gate.admit(aPendingRow())).isEmpty();
    }

    /**
     * A row written by an older deploy can carry a message type code this deploy no longer declares. It must be
     * suppressed, not thrown on - the gate resolving {@link MessageType} eagerly and unconditionally would have blown
     * up on such a row before even the widest check ran. Recorded as {@link SuppressionReason#UNKNOWN_TYPE}, not
     * {@link SuppressionReason#SUBJECT_OFF}: no setting caused this, and an operator debugging a missing message must
     * not go chasing a subject opt-out that was never touched.
     */
    @Test
    void aRowWithAnUnrecognizedMessageTypeIsSuppressedAsUnknownType() {

        givenOrganization(true);

        assertThat(gate.admit(aPendingRowWithCode("retired_message_type"))).contains(SuppressionReason.UNKNOWN_TYPE);
    }

    /**
     * The organization kill switch is the widest check and must run before the row's message type is even resolved: an
     * organization that switched communications off must still be able to silence a row whose type this deploy no
     * longer recognizes.
     */
    @Test
    void anOrganizationWithCommunicationsOffSilencesEvenAnUnrecognizedMessageType() {

        givenOrganization(false);

        assertThat(gate.admit(aPendingRowWithCode("retired_message_type")))
                .contains(SuppressionReason.ORGANIZATION_OFF);
    }
}
