package dev.axonia.communication.application;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.application.port.out.CurrentUser;
import dev.axonia.communication.application.port.out.NotificationPreferenceStore;
import dev.axonia.communication.application.port.out.OrganizationContext;
import dev.axonia.communication.domain.StoredPreference;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    @Mock
    NotificationPreferenceStore notificationPreferenceStore;
    @Mock
    CurrentUser currentUser;
    @Mock
    OrganizationContext organizationContext;
    @Mock
    MessageSubjectPolicies policies;

    NotificationPreferenceService service;

    @BeforeEach
    void setUp() {
        service = new NotificationPreferenceService(notificationPreferenceStore, organizationContext, currentUser,
                policies);
    }

    /**
     * {@code currentUser}/{@code organizationContext} are stubbed per test, not in {@code setUp}: the
     * {@code unsubscribe(...)} test below deliberately touches neither (that is its whole point - there is no JWT to
     * read them from), and a shared, unconditional stub would silence Mockito's strict-stubs check for it - and for
     * every other test too, the moment one of them stopped reading a collaborator it no longer needed.
     */
    private void givenTheCurrentCaller(String userId, String organizationId) {
        when(currentUser.userId()).thenReturn(userId);
        when(organizationContext.organizationId()).thenReturn(organizationId);
    }

    private void givenTheOpenTypesAreSent() {
        when(policies.types()).thenReturn(MessageType.open());
    }

    @Test
    void anAbsentRowMeansEnabled() {

        givenTheCurrentCaller("user-1", "default");
        givenTheOpenTypesAreSent();
        when(notificationPreferenceStore.storedFor("default", "user-1")).thenReturn(List.of());

        assertThat(service.preferences()).allSatisfy(p -> assertThat(p.enabled()).isTrue())
                .hasSize(MessageType.open().size());
    }

    @Test
    void turningOneOffLeavesTheOthersOn() {

        givenTheCurrentCaller("user-1", "default");
        givenTheOpenTypesAreSent();
        when(notificationPreferenceStore.storedFor("default", "user-1"))
                .thenReturn(List.of(new StoredPreference("session_wrap_up", false)));

        assertThat(service.preferences()).filteredOn(p -> MessageType.SESSION_WRAP_UP.equals(p.type())).singleElement()
                .satisfies(p -> assertThat(p.enabled()).isFalse());
        assertThat(service.preferences()).filteredOn(p -> MessageType.SESSION_UPCOMING.equals(p.type())).singleElement()
                .satisfies(p -> assertThat(p.enabled()).isTrue());
    }

    @Test
    void anUnknownStoredMessageTypeIsSkippedRatherThanFailingTheWholeRead() {

        givenTheCurrentCaller("user-1", "default");
        givenTheOpenTypesAreSent();
        when(notificationPreferenceStore.storedFor("default", "user-1"))
                .thenReturn(List.of(new StoredPreference("retired_message_type", false)));

        assertThat(service.preferences()).allSatisfy(p -> assertThat(p.enabled()).isTrue())
                .hasSize(MessageType.open().size());
    }

    @Test
    void settingAPreferenceUpsertsOneRowThroughASingleAtomicStatement() {

        givenTheCurrentCaller("user-1", "default");
        when(policies.declares(MessageType.SESSION_WRAP_UP)).thenReturn(true);

        service.set(MessageType.SESSION_WRAP_UP, false);

        verify(notificationPreferenceStore).upsert("default", "user-1", MessageType.SESSION_WRAP_UP, false);
    }

    /**
     * The unsubscribe path's write, invoked with an explicit user id and Organization id rather than through
     * {@link CurrentUser}/{@link OrganizationContext} - {@code currentUser} and {@code organizationContext} are never
     * touched here, which is the whole point: this method exists because the anonymous unsubscribe request has neither.
     */
    @Test
    void unsubscribingWritesTheGivenUserAndOrganizationRatherThanTheCurrentOnes() {

        when(policies.declares(MessageType.SESSION_WRAP_UP)).thenReturn(true);

        service.unsubscribe("acme", "other-user", MessageType.SESSION_WRAP_UP);

        verify(notificationPreferenceStore).upsert("acme", "other-user", MessageType.SESSION_WRAP_UP, false);
    }

    /**
     * The bug this whole class of write exists to close off: two callers racing the identical
     * {@code (organization, user, type)} row - the mail client's one-click {@code List-Unsubscribe} POST and the person
     * also clicking the footer link in the same message, say - must never surface as a
     * {@code communication_preference_unique} {@code DuplicateKeyException}. A read-then-write {@code find} followed by
     * {@code save} cannot promise that; one atomic {@code INSERT ... ON CONFLICT DO UPDATE} statement can, so this
     * asserts the write is exactly that one statement, not a find first.
     */
    @Test
    void unsubscribingNeverReadsTheRowFirst() {

        when(policies.declares(MessageType.SESSION_WRAP_UP)).thenReturn(true);

        service.unsubscribe("acme", "other-user", MessageType.SESSION_WRAP_UP);

        verify(notificationPreferenceStore, never()).storedFor(any(), any(), any());
        verify(notificationPreferenceStore, never()).storedFor(any(), any());
    }

    @Test
    void setRefusesATypeThisInstallationDoesNotSend() {

        assertThatThrownBy(() -> service.set(MessageType.of("not_sent_here"), false))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("UNKNOWN_MESSAGE_TYPE");
        verifyNoInteractions(notificationPreferenceStore);
    }

    /**
     * A signed unsubscribe link names a well-formed type this installation does not send - one minted before the type
     * stopped being sent, say. The token verifies, and the write is skipped here instead of storing a preference for a
     * message nothing sends.
     */
    @Test
    void unsubscribeIgnoresATypeThisInstallationDoesNotSend() {

        service.unsubscribe("acme", "other-user", MessageType.of("not_sent_here"));

        verifyNoInteractions(notificationPreferenceStore);
    }
}
