package dev.axonia.events;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.ConflictException;
import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.ValidationException;
import dev.axonia.communication.MessageOutcome;
import dev.axonia.communication.MessageOutcomePort;
import dev.axonia.events.internal.AttendanceRepository;
import dev.axonia.events.internal.ReleasableMessage;
import dev.axonia.events.internal.SectionTypeRepository;
import dev.axonia.events.internal.SeriesRepository;
import dev.axonia.events.internal.SessionAuthorizer;
import dev.axonia.events.internal.SessionBatchLookups;
import dev.axonia.events.internal.SessionEntity;
import dev.axonia.events.internal.SessionMapper;
import dev.axonia.events.internal.SessionMessagePreviewer;
import dev.axonia.events.internal.SessionRepository;
import dev.axonia.events.mother.SessionEntityMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Everything an organizer decides about a session's outgoing member mail. Split out of {@code SessionServiceTest}
 * alongside the service itself; the tests below were moved rather than rewritten, plus the ones the parameterised
 * preview needs.
 */
@MockitoSettings
class SessionReleaseServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-10T09:00:00Z");

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SeriesRepository seriesRepository;
    @Mock
    private SectionTypeRepository sectionTypeRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private SessionAuthorizer sessionAuthorizer;
    @Mock
    private SessionMessagePreviewer previewer;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private MessageOutcomePort messageOutcomePort;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private OrganizationContext organizationContext;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private SessionReleaseService sut;

    @BeforeEach
    void wire() {

        var mapper = new SessionMapper(communityAccess, messageOutcomePort,
                new SessionBatchLookups(sectionTypeRepository, attendanceRepository, seriesRepository));
        sut = new SessionReleaseService(sessionRepository, mapper, sessionAuthorizer, previewer, communityAccess,
                currentUser, organizationContext, clock);
    }

    /**
     * Every method here answers {@link SessionMapper#toSessionDetail}, which resolves both release statuses through
     * {@code communityAccess} and {@code messageOutcomePort} - stubbed here as "nobody enqueued, nobody home" so tests
     * asserting on the persisted entity, not the card, do not also have to reason about those reads.
     */
    private void stubNoReleaseActivity(Long sessionId, Long communityId) {

        when(communityAccess.memberUserIds("default", communityId)).thenReturn(List.of());
        when(messageOutcomePort.outcomeFor(any(), eq(String.valueOf(sessionId))))
                .thenReturn(MessageOutcome.builder().build());
    }

    private void givenHeldSession(Long id) {

        var entity = SessionEntityMother.any().id(id).communityId(7L).status("HELD")
                .scheduledAt(NOW.minus(1, ChronoUnit.HOURS)).closedAt(NOW.minus(1, ChronoUnit.HOURS))
                .wrapUpReleaseAt(NOW.plus(47, ChronoUnit.HOURS)).sections(List.of()).build();
        given(id, entity);
    }

    private void givenScheduledPastSession(Long id) {

        var entity = SessionEntityMother.any().id(id).communityId(7L).status("SCHEDULED")
                .scheduledAt(NOW.minus(1, ChronoUnit.HOURS)).sections(List.of()).build();
        given(id, entity);
    }

    /**
     * A session still ahead of the clock, carrying whatever reminder intent the test needs. Every release-intent test
     * shares it so the fixture cannot drift between "untouched" and "deliberate".
     */
    private void givenScheduledSession(Long id, Instant scheduledAt, Instant upcomingReleaseAt) {

        var entity = SessionEntityMother.any().id(id).communityId(7L).status("SCHEDULED").scheduledAt(scheduledAt)
                .upcomingReleaseAt(upcomingReleaseAt).sections(List.of()).build();
        given(id, entity);
    }

    private void given(Long id, SessionEntity entity) {

        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", id)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
    }

    @Test
    void anOrganizerMovesTheRelease() {

        givenHeldSession(42L);
        var friday = Instant.parse("2026-03-13T08:00:00Z");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubNoReleaseActivity(42L, 7L);

        sut.setWrapUpRelease(42L, new WrapUpReleaseRequest(friday));

        verify(sessionRepository).save(argThat(s -> friday.equals(s.getWrapUpReleaseAt())));
        verify(sessionAuthorizer).requireCommunicationManager(any(SessionEntity.class), eq("orga"));
    }

    @Test
    void clearingTheReleaseSuppressesTheWrapUpForGood() {

        givenHeldSession(42L);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubNoReleaseActivity(42L, 7L);

        sut.setWrapUpRelease(42L, new WrapUpReleaseRequest(null));

        verify(sessionRepository).save(argThat(s -> s.getWrapUpReleaseAt() == null));
    }

    /**
     * A past pick - an organizer's deliberate "send it Friday" that was Friday last week, "Send it now" racing the
     * round trip, or a client clock running slow - must not be stored verbatim. `findWrapUpSessionIds` only claims rows
     * above the production watermark (`wrap_up_release_at > :since`), which trails real time by up to the produce
     * interval; anything further in the past than that falls below every future watermark and is never claimed again.
     * Clamping to the server's own clock is what keeps it claimable on the very next produce tick.
     */
    @Test
    void aPastReleaseIsClampedToNowSoItStaysClaimable() {

        givenHeldSession(42L);
        var past = NOW.minus(10, ChronoUnit.MINUTES);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubNoReleaseActivity(42L, 7L);

        sut.setWrapUpRelease(42L, new WrapUpReleaseRequest(past));

        verify(sessionRepository).save(argThat(s -> NOW.equals(s.getWrapUpReleaseAt())));
    }

    @Test
    void aFutureReleaseIsStoredUnchanged() {

        givenHeldSession(42L);
        var friday = NOW.plus(3, ChronoUnit.DAYS);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubNoReleaseActivity(42L, 7L);

        sut.setWrapUpRelease(42L, new WrapUpReleaseRequest(friday));

        verify(sessionRepository).save(argThat(s -> friday.equals(s.getWrapUpReleaseAt())));
    }

    @Test
    void setWrapUpRelease_notHeldRejected() {

        givenScheduledPastSession(42L);

        assertThatThrownBy(() -> sut.setWrapUpRelease(42L, new WrapUpReleaseRequest(NOW)))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("SESSION_NOT_HELD");
    }

    @Test
    void setUpcomingRelease_organizerPicksAnInstantInsideTheWindow() {

        givenScheduledSession(42L, NOW.plus(10, ChronoUnit.DAYS), null);
        var thursday = NOW.plus(7, ChronoUnit.DAYS);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubNoReleaseActivity(42L, 7L);

        sut.setUpcomingRelease(42L, new UpcomingReleaseRequest(false, thursday));

        verify(sessionRepository).save(argThat(s -> thursday.equals(s.getUpcomingReleaseAt())));
        verify(sessionAuthorizer).requireCommunicationManager(any(SessionEntity.class), eq("orga"));
    }

    /**
     * "Send it now" is answered by the server's own {@link Clock}, never by an instant the browser put in the payload:
     * a pick in the past is refused, and a browser two seconds slow would otherwise have the one answer that must work
     * in every state a reminder can still be sent from rejected as a stale pick.
     */
    @Test
    void setUpcomingRelease_sendNowIsStampedFromTheServerClock() {

        givenScheduledSession(42L, NOW.plus(10, ChronoUnit.DAYS), null);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubNoReleaseActivity(42L, 7L);

        sut.setUpcomingRelease(42L, new UpcomingReleaseRequest(true, null));

        verify(sessionRepository).save(argThat(s -> NOW.equals(s.getUpcomingReleaseAt())));
    }

    @Test
    void setUpcomingRelease_nullGivesThisSessionNoReminder() {

        givenScheduledSession(42L, NOW.plus(10, ChronoUnit.DAYS), NOW.plus(7, ChronoUnit.DAYS));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubNoReleaseActivity(42L, 7L);

        sut.setUpcomingRelease(42L, new UpcomingReleaseRequest(false, null));

        verify(sessionRepository).save(argThat(s -> s.getUpcomingReleaseAt() == null));
    }

    /**
     * The lower bound, and the deliberate difference from {@link SessionReleaseService#setWrapUpRelease}, which clamps
     * a past pick up to the server clock instead. A reminder is only useful before the thing it announces, so a past
     * pick is a mistake worth refusing rather than reinterpreting as "send it now".
     */
    @Test
    void setUpcomingRelease_refusesAPickThatHasAlreadyPassed() {

        givenScheduledSession(42L, NOW.plus(10, ChronoUnit.DAYS), null);

        assertThatThrownBy(
                () -> sut.setUpcomingRelease(42L, new UpcomingReleaseRequest(false, NOW.minus(1, ChronoUnit.MINUTES))))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("UPCOMING_RELEASE_IN_PAST");
        verify(sessionRepository, never()).save(any());
    }

    /** The bound is strict at both ends: {@code now} itself is already gone by the time anything could claim it. */
    @Test
    void setUpcomingRelease_refusesAPickAtThisVeryInstant() {

        givenScheduledSession(42L, NOW.plus(10, ChronoUnit.DAYS), null);

        assertThatThrownBy(() -> sut.setUpcomingRelease(42L, new UpcomingReleaseRequest(false, NOW)))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("UPCOMING_RELEASE_IN_PAST");
        verify(sessionRepository, never()).save(any());
    }

    /** The upper bound: a notice that arrives after the session has begun is not a notice. */
    @Test
    void setUpcomingRelease_refusesAPickAfterTheSessionStarts() {

        givenScheduledSession(42L, NOW.plus(10, ChronoUnit.DAYS), null);

        assertThatThrownBy(
                () -> sut.setUpcomingRelease(42L, new UpcomingReleaseRequest(false, NOW.plus(11, ChronoUnit.DAYS))))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("UPCOMING_RELEASE_AFTER_START");
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void setUpcomingRelease_refusesAPickExactlyOnTheSessionStart() {

        var start = NOW.plus(10, ChronoUnit.DAYS);
        givenScheduledSession(42L, start, null);

        assertThatThrownBy(() -> sut.setUpcomingRelease(42L, new UpcomingReleaseRequest(false, start)))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("UPCOMING_RELEASE_AFTER_START");
        verify(sessionRepository, never()).save(any());
    }

    /**
     * Once the session has started no reminder can go out for it at all - {@code findUpcomingNoticeSessionIds} keeps
     * its {@code scheduled_at > :now} guard - so even "send now" and "do not send" are refused here rather than storing
     * a decision that could only ever be a lie.
     */
    @Test
    void setUpcomingRelease_refusedOnceTheSessionHasStarted() {

        givenScheduledPastSession(42L);

        assertThatThrownBy(() -> sut.setUpcomingRelease(42L, new UpcomingReleaseRequest(true, null)))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("SESSION_ALREADY_STARTED");
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void setUpcomingRelease_refusedOnASessionThatIsNoLongerScheduled() {

        given(42L, SessionEntityMother.any().id(42L).communityId(7L).status("CANCELLED")
                .scheduledAt(NOW.plus(10, ChronoUnit.DAYS)).sections(List.of()).build());

        assertThatThrownBy(() -> sut.setUpcomingRelease(42L, new UpcomingReleaseRequest(true, null)))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("SESSION_NOT_SCHEDULED");
        verify(sessionRepository, never()).save(any());
    }

    /**
     * The three answers are mutually exclusive, so a payload asserting two of them is refused rather than resolved by
     * precedence. The pick here is a perfectly good one: a version that let {@code sendNow} win would silently discard
     * it, and this test would be unable to tell that apart from the refusal of a past pick.
     */
    @Test
    void setUpcomingRelease_refusesAPayloadThatBothSendsNowAndPicksAnInstant() {

        givenScheduledSession(42L, NOW.plus(10, ChronoUnit.DAYS), null);

        assertThatThrownBy(
                () -> sut.setUpcomingRelease(42L, new UpcomingReleaseRequest(true, NOW.plus(7, ChronoUnit.DAYS))))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("UPCOMING_RELEASE_AMBIGUOUS");
        verify(sessionRepository, never()).save(any());
    }

    /**
     * Like {@link SessionReleaseService#setWrapUpRelease}, this returns the detail projection: a caller reading the
     * response for "did that take" needs {@code upcomingStatus} populated rather than the null that means "not the
     * detail read".
     */
    @Test
    void setUpcomingRelease_answersWithTheDetailProjection() {

        givenScheduledSession(42L, NOW.plus(10, ChronoUnit.DAYS), null);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(communityAccess.memberUserIds("default", 7L)).thenReturn(List.of("alice", "bob"));
        when(messageOutcomePort.outcomeFor(any(), eq("42"))).thenReturn(MessageOutcome.builder().build());

        var actual = sut.setUpcomingRelease(42L, new UpcomingReleaseRequest(true, null));

        assertThat(actual.upcomingStatus().state()).isEqualTo(ReleaseState.SCHEDULED);
        assertThat(actual.upcomingStatus().recipientCount()).isEqualTo(2);
    }

    @Test
    void preview_organizerSeesTheRenderedWrapUp() {

        givenHeldSession(42L);
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(7L);
        var entity = sessionRepository.findByOrganizationIdAndId("default", 42L).orElseThrow();
        var expected = new MessagePreview("Frontend Dojo: what happened", "<html>preview</html>");
        when(previewer.preview("default", entity, "orga", ReleasableMessage.WRAP_UP)).thenReturn(expected);

        var actual = sut.preview("frontend", 42L, "session_wrap_up");

        assertThat(actual).isEqualTo(expected);
        verify(sessionAuthorizer).requireCommunicationManager(entity, "orga");
    }

    /**
     * The same endpoint, the same authorisation, the same everything, with the message type as a parameter. The
     * reminder is previewable on a session that has not happened yet, which is the only state it is ever previewed in.
     */
    @Test
    void preview_organizerSeesTheRenderedReminder() {

        givenScheduledSession(42L, NOW.plus(10, ChronoUnit.DAYS), NOW.plus(7, ChronoUnit.DAYS));
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(7L);
        var entity = sessionRepository.findByOrganizationIdAndId("default", 42L).orElseThrow();
        var expected = new MessagePreview("Frontend Dojo is coming up", "<html>reminder</html>");
        when(previewer.preview("default", entity, "orga", ReleasableMessage.UPCOMING)).thenReturn(expected);

        var actual = sut.preview("frontend", 42L, "session_upcoming");

        assertThat(actual).isEqualTo(expected);
        verify(sessionAuthorizer).requireCommunicationManager(entity, "orga");
    }

    /**
     * An unrecognised type is refused outright, and refused BEFORE the session is even looked up: a default - "not one
     * I know, so render the wrap-up" - would pass every other test in this class while showing somebody a message the
     * URL never asked for. {@code verifyNoInteractions} on the previewer is the half that catches a lenient fallback;
     * refusing before the lookup is what keeps the answer the same whether or not the session exists.
     */
    @Test
    void preview_refusesAMessageTypeNoOrganizerReleases() {

        assertThatThrownBy(() -> sut.preview("frontend", 42L, "session_awaiting_close"))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("UNSUPPORTED_MESSAGE_TYPE");
        verify(previewer, never()).preview(any(), any(), any(), any());
        verify(sessionRepository, never()).findByOrganizationIdAndId(any(), any());
    }

    @Test
    void preview_refusesAMessageTypeThatDoesNotExist() {

        assertThatThrownBy(() -> sut.preview("frontend", 42L, "banana")).isInstanceOf(ValidationException.class)
                .extracting("code").isEqualTo("UNSUPPORTED_MESSAGE_TYPE");
        verify(previewer, never()).preview(any(), any(), any(), any());
    }

    @Test
    void preview_nonOrganizerForbidden() {

        givenHeldSession(42L);
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(7L);
        doThrow(new ForbiddenException("Only an organizer may do this")).when(sessionAuthorizer)
                .requireCommunicationManager(any(SessionEntity.class), eq("orga"));

        assertThatThrownBy(() -> sut.preview("frontend", 42L, "session_wrap_up"))
                .isInstanceOf(ForbiddenException.class);
        verify(previewer, never()).preview(any(), any(), any(), any());
    }

    /**
     * The reminder is exactly as private as the wrap-up: it carries the session's presenters by name and a link into a
     * community the caller may not belong to, so the authorisation floor is asserted per message type rather than once
     * for whichever one happened to be tested.
     */
    @Test
    void preview_nonOrganizerForbiddenForTheReminderToo() {

        givenScheduledSession(42L, NOW.plus(10, ChronoUnit.DAYS), null);
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(7L);
        doThrow(new ForbiddenException("Only an organizer may do this")).when(sessionAuthorizer)
                .requireCommunicationManager(any(SessionEntity.class), eq("orga"));

        assertThatThrownBy(() -> sut.preview("frontend", 42L, "session_upcoming"))
                .isInstanceOf(ForbiddenException.class);
        verify(previewer, never()).preview(any(), any(), any(), any());
    }

    @Test
    void preview_sessionNotFound() {

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(7L);
        when(sessionRepository.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.preview("frontend", 99L, "session_wrap_up")).isInstanceOf(NotFoundException.class);
    }

    /**
     * A slug naming a real community that is not this session's own must 404 exactly like an unknown slug or an unknown
     * session id does - the same consistency every other {@code /communities/{slug}/...} method in this module already
     * gives its own slug argument, rather than accepting a slug and silently ignoring it. The actual mismatch check
     * lives on {@link dev.axonia.events.internal.SessionAuthorizer#requireCommunityMatches}, proven by that class's own
     * test; this only proves {@code preview} calls it before authorising or rendering anything.
     */
    @Test
    void preview_wrongSlugForThisSessionsCommunityNotFound() {

        var entity = SessionEntityMother.any().id(42L).communityId(7L).status("HELD")
                .scheduledAt(NOW.minus(1, ChronoUnit.HOURS)).sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 42L)).thenReturn(Optional.of(entity));
        when(communityAccess.communityIdForSlug("backend")).thenReturn(99L);
        doThrow(new NotFoundException("Session not found: 42")).when(sessionAuthorizer).requireCommunityMatches(entity,
                99L);

        assertThatThrownBy(() -> sut.preview("backend", 42L, "session_wrap_up")).isInstanceOf(NotFoundException.class);
        verify(sessionAuthorizer, never()).requireCommunicationManager(any(), any());
        verify(previewer, never()).preview(any(), any(), any(), any());
    }

    @Test
    void preview_matchingSlugChecksCommunityBeforeAuthorising() {

        givenHeldSession(42L);
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(7L);
        var entity = sessionRepository.findByOrganizationIdAndId("default", 42L).orElseThrow();
        when(previewer.preview("default", entity, "orga", ReleasableMessage.WRAP_UP))
                .thenReturn(new MessagePreview("subject", "html"));

        sut.preview("frontend", 42L, "session_wrap_up");

        verify(sessionAuthorizer).requireCommunityMatches(entity, 7L);
    }

    /**
     * The preview is not gated on session state, unlike {@link SessionReleaseService#setWrapUpRelease}: an organizer
     * previewing before the session is even closed must still see something rather than a conflict error.
     * {@link SessionMessagePreviewer} is what actually tolerates a session with no wrap-up content - this only proves
     * {@code preview} never adds a state guard of its own on top.
     */
    @Test
    void preview_worksEvenWhenTheSessionIsNotYetHeld() {

        givenScheduledPastSession(42L);
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(7L);
        var entity = sessionRepository.findByOrganizationIdAndId("default", 42L).orElseThrow();
        var expected = new MessagePreview("This session: what happened", "<html></html>");
        when(previewer.preview("default", entity, "orga", ReleasableMessage.WRAP_UP)).thenReturn(expected);

        var actual = sut.preview("frontend", 42L, "session_wrap_up");

        assertThat(actual).isEqualTo(expected);
    }
}
