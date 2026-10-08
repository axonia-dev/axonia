package dev.axonia.events;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.ApplicationEventPublisher;

import dev.axonia.ConflictException;
import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.communication.MessageOutcomePort;
import dev.axonia.community.CommunicationSettings;
import dev.axonia.events.internal.ActionItemRepository;
import dev.axonia.events.internal.AttendanceRepository;
import dev.axonia.events.internal.SectionTypeRef;
import dev.axonia.events.internal.SectionTypeRepository;
import dev.axonia.events.internal.SeriesEntity;
import dev.axonia.events.internal.SeriesRepository;
import dev.axonia.events.internal.SessionBatchLookups;
import dev.axonia.events.internal.SessionCreation;
import dev.axonia.events.internal.SessionEntity;
import dev.axonia.events.internal.SessionMapper;
import dev.axonia.events.internal.SessionRepository;
import dev.axonia.events.internal.UpcomingReleasePolicy;
import dev.axonia.events.mother.SectionTypeEntityMother;
import dev.axonia.events.mother.SeriesEntityMother;
import dev.axonia.events.mother.SessionEntityMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class OccurrenceSkipServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SeriesRepository seriesRepository;
    @Mock
    private SectionTypeRepository sectionTypeRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private ActionItemRepository actionItemRepository;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private MessageOutcomePort messageOutcomePort;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OccurrenceSkipService sut;

    @BeforeEach
    void wire() {

        var mapper = new SessionMapper(communityAccess, messageOutcomePort,
                new SessionBatchLookups(sectionTypeRepository, attendanceRepository, seriesRepository));
        sut = new OccurrenceSkipService(sessionRepository, seriesRepository, mapper,
                new SessionCreation(new UpcomingReleasePolicy(communityAccess, FIXED_CLOCK)), communityAccess,
                currentUser, organizationContext, eventPublisher, FIXED_CLOCK, actionItemRepository);
    }

    /** The community's announcement lead time, for the one path here that materialises a brand-new session row. */
    private void leadDays(long communityId, int days) {

        when(communityAccess.communicationSettings("default", communityId))
                .thenReturn(new CommunicationSettings(true, true, days, 24));
    }

    private void talk(Long id) {

        when(sectionTypeRepository.findByOrganizationIdAndIdIn(eq("default"), eq(Set.of(id))))
                .thenReturn(List.of(SectionTypeEntityMother.any().id(id).label("Talk").build()));
    }

    @Test
    void skip_freshAnchorCreatesSkippedRow() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).defaultStructure(List.of()).build();
        var anchor = LocalDate.of(2099, 12, 12);
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.isOrganizer(1L, "orga")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate("default", 5L, anchor))
                .thenReturn(Optional.empty());
        leadDays(1L, 3);
        when(sessionRepository.save(any()))
                .thenAnswer(inv -> ((SessionEntity) inv.getArgument(0)).toBuilder().id(88L).build());

        var actual = sut.skip(5L, anchor);

        assertThat(actual.id()).isEqualTo(88L);
        assertThat(actual.status()).isEqualTo(SessionStatus.SKIPPED);
        verify(sessionRepository)
                .save(argThat(e -> "SKIPPED".equals(e.getStatus()) && anchor.equals(e.getOccurrenceDate())));
    }

    /**
     * Skipping is the third door into a session row, and it writes the time fact materialisation would have written
     * rather than one of its own. Unskip hands that same row back as a live occurrence, so a skip that guessed here
     * would return a session showing a clock nobody ever chose.
     */
    @Test
    void skip_seriesWithNoPreferredTime_materialisesAnUntimedRow() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).preferredTime(null).defaultStructure(List.of())
                .build();
        var anchor = LocalDate.of(2099, 12, 12);
        givenASkippableOccurrence(series, anchor);

        var actual = sut.skip(5L, anchor);

        assertThat(actual.hasTime()).isFalse();
        verify(sessionRepository).save(argThat(e -> !e.isHasTime()));
    }

    @Test
    void skip_seriesWithAPreferredTime_materialisesATimedRow() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).preferredTime(LocalTime.of(18, 0))
                .defaultStructure(List.of()).build();
        var anchor = LocalDate.of(2099, 12, 12);
        givenASkippableOccurrence(series, anchor);

        var actual = sut.skip(5L, anchor);

        assertThat(actual.hasTime()).isTrue();
        verify(sessionRepository).save(argThat(e -> e.isHasTime()));
    }

    /** A future occurrence of a series an organizer runs, with no row at the anchor yet. */
    private void givenASkippableOccurrence(SeriesEntity series, LocalDate anchor) {

        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.isOrganizer(1L, "orga")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate("default", 5L, anchor))
                .thenReturn(Optional.empty());
        leadDays(1L, 3);
        when(sessionRepository.save(any()))
                .thenAnswer(inv -> ((SessionEntity) inv.getArgument(0)).toBuilder().id(88L).build());
    }

    /**
     * Skipping an occurrence that was never materialised is the third path that brings a session row into existence, so
     * it meets the default release intent like the other two. It matters on unskip, where the restored row is
     * {@code SCHEDULED} again and an unstamped one would be permanently silent for no reason anyone chose - pinned by
     * {@link #unskip_preservesTheUpcomingReleaseIntent}.
     */
    @Test
    void skip_freshAnchorStampsTheDefaultUpcomingRelease() {

        // given a community that announces three days ahead
        var series = SeriesEntityMother.any().id(5L).communityId(1L).defaultStructure(List.of()).build();
        var anchor = LocalDate.of(2099, 12, 12);
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.isOrganizer(1L, "orga")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate("default", 5L, anchor))
                .thenReturn(Optional.empty());
        leadDays(1L, 3);
        when(sessionRepository.save(any()))
                .thenAnswer(inv -> ((SessionEntity) inv.getArgument(0)).toBuilder().id(88L).build());

        // when
        var actual = sut.skip(5L, anchor);

        // then - the series' preferred time is 18:00, so the occurrence starts at 2099-12-12T18:00Z
        var expected = Instant.parse("2099-12-09T18:00:00Z");
        assertThat(actual.upcomingReleaseAt()).isEqualTo(expected);
        verify(sessionRepository).save(argThat(e -> expected.equals(e.getUpcomingReleaseAt())));
    }

    @Test
    void skip_nonOrganizerForbidden() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).defaultStructure(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("member");
        when(communityAccess.isOrganizer(1L, "member")).thenReturn(false);

        assertThatThrownBy(() -> sut.skip(5L, LocalDate.of(2099, 12, 12))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void skip_pastOccurrenceConflicts() {

        // SKIPPED cannot be applied retroactively: a past materialized occurrence that was never
        // answered must go through cancel, not skip. This refusal is driven by the injected Clock, not by isHeld,
        // which no longer means "past" now that HELD is an asserted fact.
        var series = SeriesEntityMother.any().id(5L).communityId(1L).defaultStructure(List.of()).build();
        var pastDate = LocalDate.of(2000, 1, 1);
        var pastSession = SessionEntityMother.any().id(9L).seriesId(5L).occurrenceDate(pastDate)
                .scheduledAt(Instant.parse("2000-01-01T00:00:00Z")).sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.isOrganizer(1L, "orga")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate("default", 5L, pastDate))
                .thenReturn(Optional.of(pastSession));

        assertThatThrownBy(() -> sut.skip(5L, pastDate)).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("OCCURRENCE_IN_PAST");
    }

    @Test
    void skip_pastVirtualOccurrenceConflicts() {

        // The projected (never-materialized) path: no row exists yet, so the refusal is answered from the occurrence's
        // own instant rather than from an entity. The older shape derived it from a transient row whose status was
        // null, which made the then isHeld-driven guard permissive here; the past-date check closes that off directly.
        // No communication settings are stubbed on purpose: a request that only ever 409s must not read them, which is
        // what keeps a pure refusal from paying to materialise a row nobody will ever save.
        var series = SeriesEntityMother.any().id(5L).communityId(1L).defaultStructure(List.of()).build();
        var pastDate = LocalDate.of(2000, 1, 1);
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.isOrganizer(1L, "orga")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate("default", 5L, pastDate))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.skip(5L, pastDate)).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("OCCURRENCE_IN_PAST");
        verifyNoMoreInteractions(communityAccess);
    }

    @Test
    void skip_assertedHeldOccurrenceConflicts() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).defaultStructure(List.of()).build();
        var pastDate = LocalDate.of(2000, 1, 1);
        var heldSession = SessionEntityMother.any().id(9L).seriesId(5L).occurrenceDate(pastDate).status("HELD")
                .scheduledAt(Instant.parse("2000-01-01T00:00:00Z")).sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.isOrganizer(1L, "orga")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate("default", 5L, pastDate))
                .thenReturn(Optional.of(heldSession));

        assertThatThrownBy(() -> sut.skip(5L, pastDate)).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("SESSION_ALREADY_HELD");
    }

    @Test
    void skip_publishesSessionSkipped() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).defaultStructure(List.of()).build();
        var anchor = LocalDate.of(2099, 12, 12);
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.isOrganizer(1L, "orga")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate("default", 5L, anchor))
                .thenReturn(Optional.empty());
        leadDays(1L, 3);
        when(sessionRepository.save(any()))
                .thenAnswer(inv -> ((SessionEntity) inv.getArgument(0)).toBuilder().id(88L).build());

        sut.skip(5L, anchor);

        verify(eventPublisher)
                .publishEvent(argThat((Object e) -> e instanceof SessionSkipped ss && ss.sessionId().equals(88L)));
    }

    @Test
    void unskip_futureRestoresToScheduled() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).defaultStructure(List.of()).build();
        var anchor = LocalDate.of(2099, 12, 12);
        var skipped = SessionEntityMother.any().id(42L).seriesId(5L).occurrenceDate(anchor).status("SKIPPED")
                .sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.isOrganizer(1L, "orga")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate("default", 5L, anchor))
                .thenReturn(Optional.of(skipped));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var actual = sut.unskip(5L, anchor);

        assertThat(actual.status()).isEqualTo(SessionStatus.SCHEDULED);
        verify(sessionRepository).save(argThat(e -> "SCHEDULED".equals(e.getStatus())));
    }

    /**
     * The other half of {@link #skip_freshAnchorStampsTheDefaultUpcomingRelease}: restoring an occurrence hands back a
     * {@code SCHEDULED} row, and the reminder the row was born with has to survive that. Held structurally today,
     * because unskip rewrites a loaded row through {@code toBuilder()}; a refactor that rebuilds the row instead would
     * drop the intent silently and turn this red.
     */
    @Test
    void unskip_preservesTheUpcomingReleaseIntent() {

        // given a skipped occurrence carrying the release intent it was created with
        var series = SeriesEntityMother.any().id(5L).communityId(1L).defaultStructure(List.of()).build();
        var anchor = LocalDate.of(2099, 12, 12);
        var releaseAt = Instant.parse("2099-12-09T18:00:00Z");
        var skipped = SessionEntityMother.any().id(42L).seriesId(5L).occurrenceDate(anchor).status("SKIPPED")
                .upcomingReleaseAt(releaseAt).sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.isOrganizer(1L, "orga")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate("default", 5L, anchor))
                .thenReturn(Optional.of(skipped));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.unskip(5L, anchor);

        // then
        assertThat(actual.upcomingReleaseAt()).isEqualTo(releaseAt);
        verify(sessionRepository).save(argThat(e -> releaseAt.equals(e.getUpcomingReleaseAt())));
    }

    @Test
    void unskip_pastRestoresToScheduledAwaitingClose() {

        // HELD is asserted, not derived: restoring a past occurrence hands it back as an open question (SCHEDULED),
        // not as retroactively HELD - the organizer still has to say what actually happened.
        var series = SeriesEntityMother.any().id(5L).communityId(1L).defaultStructure(List.of()).build();
        var pastDate = LocalDate.of(2000, 1, 1);
        var skipped = SessionEntityMother.any().id(9L).seriesId(5L).occurrenceDate(pastDate)
                .scheduledAt(Instant.parse("2000-01-01T00:00:00Z")).status("SKIPPED").sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.isOrganizer(1L, "orga")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate("default", 5L, pastDate))
                .thenReturn(Optional.of(skipped));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var actual = sut.unskip(5L, pastDate);

        assertThat(actual.status()).isEqualTo(SessionStatus.SCHEDULED);
    }

    @Test
    void unskip_emptySectionsResnapshotsFromSeries() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L)
                .defaultStructure(List
                        .of(SectionTypeRef.builder().sectionTypeId(1L).title("Main talk").durationMinutes(30).build()))
                .build();
        var anchor = LocalDate.of(2099, 12, 12);
        var skipped = SessionEntityMother.any().id(42L).seriesId(5L).occurrenceDate(anchor).status("SKIPPED")
                .sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.isOrganizer(1L, "orga")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate("default", 5L, anchor))
                .thenReturn(Optional.of(skipped));
        talk(1L);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var actual = sut.unskip(5L, anchor);

        assertThat(actual.sections()).hasSize(1);
        assertThat(actual.sections().get(0).title()).isEqualTo("Main talk");
    }

    @Test
    void unskip_nonEmptySectionsPreserved() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).defaultStructure(List
                .of(SectionTypeRef.builder().sectionTypeId(99L).title("Series default").durationMinutes(30).build()))
                .build();
        var anchor = LocalDate.of(2099, 12, 12);
        var entitySection = SessionEntityMother.section(7L);
        var skipped = SessionEntityMother.any().id(42L).seriesId(5L).occurrenceDate(anchor).status("SKIPPED")
                .sections(List.of(entitySection)).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.isOrganizer(1L, "orga")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate("default", 5L, anchor))
                .thenReturn(Optional.of(skipped));
        talk(7L);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var actual = sut.unskip(5L, anchor);

        assertThat(actual.status()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(actual.sections()).hasSize(1);
        assertThat(actual.sections().get(0).sectionTypeId()).isEqualTo(7L);
    }

    @Test
    void unskip_notSkippedConflicts() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).defaultStructure(List.of()).build();
        var anchor = LocalDate.of(2099, 12, 12);
        var scheduled = SessionEntityMother.any().id(42L).seriesId(5L).occurrenceDate(anchor).status("SCHEDULED")
                .sections(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.isOrganizer(1L, "orga")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate("default", 5L, anchor))
                .thenReturn(Optional.of(scheduled));

        assertThatThrownBy(() -> sut.unskip(5L, anchor)).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("SESSION_NOT_SKIPPED");
    }

    @Test
    void unskip_missingRowNotFound() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).defaultStructure(List.of()).build();
        var anchor = LocalDate.of(2099, 12, 12);
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.isOrganizer(1L, "orga")).thenReturn(true);
        when(sessionRepository.findByOrganizationIdAndSeriesIdAndOccurrenceDate("default", 5L, anchor))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.unskip(5L, anchor)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void unskip_nonOrganizerForbidden() {

        var series = SeriesEntityMother.any().id(5L).communityId(1L).defaultStructure(List.of()).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(seriesRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(series));
        when(currentUser.userId()).thenReturn("member");
        when(communityAccess.isOrganizer(1L, "member")).thenReturn(false);

        assertThatThrownBy(() -> sut.unskip(5L, LocalDate.of(2099, 12, 12))).isInstanceOf(ForbiddenException.class);
    }
}
