package dev.axonia.events.adapter.out.communication;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.communication.api.MessageType;
import dev.axonia.community.api.CommunicationSettings;
import dev.axonia.events.adapter.out.persistence.SessionEntityMother;
import dev.axonia.events.adapter.out.persistence.SessionRepository;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.shared.api.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Wires a real {@link SessionSubjectResolver} around a mocked {@link SessionRepository} rather than mocking the
 * resolver itself: this class's own tests are what keep the subject-parsing rule - including the
 * {@code SESSION_AWAITING_CLOSE} colon-suffix branch - under coverage now that it lives in a shared collaborator rather
 * than a private method here.
 */
@MockitoSettings
class MessageSubjectPolicyAdapterTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private CommunityAccess communityAccess;

    private MessageSubjectPolicyAdapter sut;

    @BeforeEach
    void setUp() {

        sut = new MessageSubjectPolicyAdapter(new SessionSubjectResolver(sessionRepository), communityAccess);
    }

    /**
     * This adapter resolves {@code subjectRef} as a session id, so it must claim exactly the session-scoped types.
     * Claiming one it cannot resolve would fail closed on every row of that type; leaving one unclaimed stops the
     * application from starting, which is where that mistake belongs.
     */
    @Test
    void handles_claimsExactlyTheSessionScopedMessageTypes() {

        assertThat(sut.handles()).containsExactlyInAnyOrder(MessageType.SESSION_UPCOMING, MessageType.SESSION_WRAP_UP,
                MessageType.SESSION_AWAITING_CLOSE);
    }

    @Test
    void isEnabledFor_reflectsTheCommunitysSessionUpcomingSetting() {

        when(sessionRepository.findByOrganizationIdAndId("default", 42L))
                .thenReturn(Optional.of(SessionEntityMother.any().id(42L).communityId(7L).build()));
        when(communityAccess.communicationSettings("default", 7L))
                .thenReturn(new CommunicationSettings(false, true, 3, 48));

        assertThat(sut.isEnabledFor("default", readBack("session_upcoming"), "42")).isFalse();
    }

    @Test
    void isEnabledFor_reflectsTheCommunitysSessionWrapUpSetting() {

        when(sessionRepository.findByOrganizationIdAndId("default", 42L))
                .thenReturn(Optional.of(SessionEntityMother.any().id(42L).communityId(7L).build()));
        when(communityAccess.communicationSettings("default", 7L))
                .thenReturn(new CommunicationSettings(true, false, 3, 48));

        assertThat(sut.isEnabledFor("default", readBack("session_wrap_up"), "42")).isFalse();
    }

    @Test
    void isEnabledFor_sessionAwaitingCloseHasNoCommunitySettingSoItIsAlwaysOn() {

        when(sessionRepository.findByOrganizationIdAndId("default", 42L))
                .thenReturn(Optional.of(SessionEntityMother.any().id(42L).communityId(7L).build()));
        when(communityAccess.communicationSettings("default", 7L))
                .thenReturn(new CommunicationSettings(false, false, 3, 48));

        assertThat(sut.isEnabledFor("default", readBack("session_awaiting_close"), "42")).isTrue();
    }

    /**
     * {@code ORGANIZATION_ROUNDUP} is not this adapter's type: {@code DeliveryGate} routes it to the roundup module's
     * own policy, and {@link MessageSubjectPolicyAdapter#handles()} never claims it. Asked anyway, the answer is
     * fail-closed - asserted here so that the answer is a decision somebody made rather than a line nothing ever
     * exercises.
     */
    @Test
    void isEnabledFor_aTypeThisAdapterDoesNotClaimIsTreatedAsDisabled() {

        when(sessionRepository.findByOrganizationIdAndId("default", 42L))
                .thenReturn(Optional.of(SessionEntityMother.any().id(42L).communityId(7L).build()));
        when(communityAccess.communicationSettings("default", 7L))
                .thenReturn(new CommunicationSettings(true, true, 3, 48));

        assertThat(sut.isEnabledFor("default", readBack("organization_roundup"), "42")).isFalse();
    }

    /**
     * The second, seven-days-later {@code SESSION_AWAITING_CLOSE} nudge carries a {@code "<sessionId>:7d"}
     * {@code subjectRef} - otherwise it would collide with the first nudge under {@code communication_message_once}'s
     * {@code (organization_id, message_type, subject_ref)} uniqueness. Resolution must still find session 42, not fail
     * closed on the suffix.
     */
    @Test
    void isEnabledFor_aSecondNudgeSubjectRefStillResolvesToItsSession() {

        when(sessionRepository.findByOrganizationIdAndId("default", 42L))
                .thenReturn(Optional.of(SessionEntityMother.any().id(42L).communityId(7L).build()));
        when(communityAccess.communicationSettings("default", 7L))
                .thenReturn(new CommunicationSettings(true, true, 3, 48));

        assertThat(sut.isEnabledFor("default", readBack("session_awaiting_close"), "42:7d")).isTrue();
    }

    @Test
    void isEnabledFor_aSessionThatNoLongerExistsIsTreatedAsDisabled() {

        when(sessionRepository.findByOrganizationIdAndId("default", 42L)).thenReturn(Optional.empty());

        assertThat(sut.isEnabledFor("default", readBack("session_wrap_up"), "42")).isFalse();
    }

    @Test
    void isEnabledFor_aSubjectRefThatIsNotASessionIdIsTreatedAsDisabled() {

        assertThat(sut.isEnabledFor("default", readBack("session_wrap_up"), "not-a-session-id")).isFalse();
    }

    /**
     * A session can outlive the community it belonged to (the community was archived or removed after the session was
     * scheduled). This must fail closed the same way a missing session or an unparseable ref does, not propagate
     * {@link NotFoundException} into the drain loop - the same policy
     * {@code bookmarks.adapter.out.community.CommunityAccessAdapter} and
     * {@code recognition.adapter.out.community.CommunityAccessAdapter} already apply.
     */
    @Test
    void isEnabledFor_aCommunityThatNoLongerExistsIsTreatedAsDisabled() {

        when(sessionRepository.findByOrganizationIdAndId("default", 42L))
                .thenReturn(Optional.of(SessionEntityMother.any().id(42L).communityId(7L).build()));
        when(communityAccess.communicationSettings("default", 7L))
                .thenThrow(new NotFoundException("Community not found: 7"));

        assertThat(sut.isEnabledFor("default", readBack("session_wrap_up"), "42")).isFalse();
    }

    @Test
    void isStillAMember_delegatesToCommunityAccessForTheSessionsCommunity() {

        when(sessionRepository.findByOrganizationIdAndId("default", 42L))
                .thenReturn(Optional.of(SessionEntityMother.any().id(42L).communityId(7L).build()));
        when(communityAccess.isMember("default", 7L, "alice")).thenReturn(true);

        assertThat(sut.isStillAMember("default", "42", "alice")).isTrue();
    }

    @Test
    void isStillAMember_aSessionThatNoLongerExistsIsTreatedAsNotAMember() {

        when(sessionRepository.findByOrganizationIdAndId("default", 42L)).thenReturn(Optional.empty());

        assertThat(sut.isStillAMember("default", "42", "alice")).isFalse();
    }

    /** The type as every production caller hands it over: parsed from a stored or signed code, not the constant. */
    private static MessageType readBack(String code) {

        return MessageType.parse(code).orElseThrow();
    }
}
