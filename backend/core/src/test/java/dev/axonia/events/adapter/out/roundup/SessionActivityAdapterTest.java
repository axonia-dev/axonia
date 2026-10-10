package dev.axonia.events.adapter.out.roundup;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.events.application.port.out.UserProfileAccess;
import dev.axonia.roundup.api.spi.SessionActivity.HeldSession;
import dev.axonia.roundup.api.spi.SessionActivity.UpcomingSession;

import static org.assertj.core.api.Assertions.assertThat;

class SessionActivityAdapterTest extends CoreJdbcTestSupport {

    /**
     * Its own organization, never the shared "default": this port answers for a whole organization at once, so a row
     * another test committed there would be part of the answer.
     */
    private static final String ORG = "roundup-sessions";
    private static final String OTHER_ORG = "roundup-sessions-other";
    private static final String ERASED = "erased:0000";
    private static final String NAMELESS = "nameless";
    private static final Instant QUARTER_START = Instant.parse("2026-04-01T00:00:00Z");
    private static final Instant QUARTER_END = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant THIS_QUARTER = Instant.parse("2026-05-12T17:00:00Z");
    private static final Instant ALSO_THIS_QUARTER = Instant.parse("2026-06-09T17:00:00Z");
    private static final Instant LAST_QUARTER = Instant.parse("2026-02-10T17:00:00Z");
    private static final Instant NEXT_QUARTER = Instant.parse("2026-08-11T17:00:00Z");
    private static final Instant AFTER_NEXT_QUARTER = NEXT_QUARTER.plusSeconds(1);

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private SessionActivityAdapter sut;

    @BeforeEach
    void setUp() {

        sut = new SessionActivityAdapter(jdbc, directoryThatNames());
        seedReferenceCatalogs();
    }

    @Test
    void countsOnlyPresentersWithNoEarlierPresentationAnywhereInTheOrganization() {

        // given alice presented last quarter and again this one, in a different community both times;
        // bob presented for the first time this quarter
        var guild = community("backend-guild", ORG);
        var chapter = community("data-chapter", ORG);
        presenter(slot(section(session(guild, "HELD", LAST_QUARTER))), "alice");
        presenter(slot(section(session(chapter, "HELD", THIS_QUARTER))), "alice");
        presenter(slot(section(session(guild, "HELD", THIS_QUARTER))), "bob");

        // when
        var actual = sut.firstTimePresenterCount(ORG, QUARTER_START, QUARTER_END);

        // then
        assertThat(actual).isEqualTo(1);
    }

    @Test
    void aPresenterOfSeveralSessionsInThePeriodIsOneFirstTimer() {

        // given
        var guild = community("backend-guild", ORG);
        presenter(slot(section(session(guild, "HELD", THIS_QUARTER))), "bob");
        presenter(slot(section(session(guild, "HELD", ALSO_THIS_QUARTER))), "bob");

        // when / then: counted once, not once per talk
        assertThat(sut.firstTimePresenterCount(ORG, QUARTER_START, QUARTER_END)).isEqualTo(1);
    }

    @Test
    void aPresenterWhoseOnlyEarlierSessionWasCancelledIsStillAFirstTimer() {

        // given: an occurrence that never took place cannot make somebody a returning speaker
        var guild = community("backend-guild", ORG);
        presenter(slot(section(session(guild, "CANCELLED", LAST_QUARTER))), "bob");
        presenter(slot(section(session(guild, "HELD", THIS_QUARTER))), "bob");

        // when / then
        assertThat(sut.firstTimePresenterCount(ORG, QUARTER_START, QUARTER_END)).isEqualTo(1);
    }

    @Test
    void aPresenterBookedForALaterSessionIsNotYetCounted() {

        // given: only what happened inside the window counts
        var guild = community("backend-guild", ORG);
        presenter(slot(section(session(guild, "SCHEDULED", NEXT_QUARTER))), "bob");

        // when / then
        assertThat(sut.firstTimePresenterCount(ORG, QUARTER_START, QUARTER_END)).isZero();
    }

    @Test
    void anotherOrganizationsPresentersAreNeitherCountedNorDisqualifying() {

        // given the same person id presenting in another tenant, before and during the window
        var ours = community("backend-guild", ORG);
        var theirs = community("backend-guild", OTHER_ORG);
        presenter(slot(section(session(theirs, "HELD", LAST_QUARTER, OTHER_ORG, null))), "bob");
        presenter(slot(section(session(theirs, "HELD", THIS_QUARTER, OTHER_ORG, null))), "carol");
        presenter(slot(section(session(ours, "HELD", THIS_QUARTER))), "bob");

        // when / then: bob is a first-time presenter here, and carol is not ours to count
        assertThat(sut.firstTimePresenterCount(ORG, QUARTER_START, QUARTER_END)).isEqualTo(1);
    }

    @Test
    void heldInPeriod_namesTheCommunityTheTitleTheDateAndThePresenters() {

        // given
        var guild = community("backend-guild", ORG);
        var sectionId = section(titled(guild, THIS_QUARTER, "Structured concurrency"));
        presenter(slot(sectionId), "alice");
        presenter(slot(sectionId), "bob");

        // when
        var actual = sut.heldInPeriod(ORG, QUARTER_START, QUARTER_END);

        // then
        assertThat(actual).singleElement().satisfies(held -> {
            assertThat(held.communityId()).isEqualTo(guild);
            assertThat(held.title()).isEqualTo("Structured concurrency");
            assertThat(held.heldAt()).isEqualTo(THIS_QUARTER);
            assertThat(held.presenterNames()).containsExactly("Alice", "Bob");
        });
    }

    /**
     * The roundup's headline names how many talks a quarter carried, and a talk is a slot however many sections it is
     * spread across - a session with two sections of two and one is three talks, not two sessions' worth of anything.
     * Counted regardless of whether anybody presented it: a slot that ran with nobody named still happened.
     */
    @Test
    void heldInPeriod_countsEveryTalkOnTheAgendaAcrossEverySection() {

        // given
        var guild = community("backend-guild", ORG);
        var sessionId = session(guild, "HELD", THIS_QUARTER);
        var first = section(sessionId);
        var second = section(sessionId);
        presenter(slot(first), "alice");
        slot(first);
        presenter(slot(second), "bob");

        // when
        var actual = sut.heldInPeriod(ORG, QUARTER_START, QUARTER_END);

        // then
        assertThat(actual).singleElement().extracting(HeldSession::talkCount).isEqualTo(3);
    }

    @Test
    void heldInPeriod_aSessionThatCarriedNoAgendaHeldNoTalks() {

        // given: a session nobody ever put anything on still took place
        var guild = community("backend-guild", ORG);
        section(session(guild, "HELD", THIS_QUARTER));

        // when
        var actual = sut.heldInPeriod(ORG, QUARTER_START, QUARTER_END);

        // then
        assertThat(actual).singleElement().extracting(HeldSession::talkCount).isEqualTo(0);
    }

    @Test
    void heldInPeriod_countsOnlyItsOwnSessionsTalks() {

        // given two sessions in the window, so a count that forgot to correlate on session_id would report the total
        var guild = community("backend-guild", ORG);
        slot(section(session(guild, "HELD", THIS_QUARTER)));
        var later = session(guild, "HELD", ALSO_THIS_QUARTER);
        var laterSection = section(later);
        slot(laterSection);
        slot(laterSection);

        // when
        var actual = sut.heldInPeriod(ORG, QUARTER_START, QUARTER_END);

        // then
        assertThat(actual).extracting(HeldSession::talkCount).containsExactly(1, 2);
    }

    @Test
    void aHeldSessionNamesItsTalksInAgendaOrder() {

        // given
        var guild = community("backend-guild", ORG);
        var sessionId = session(guild, "HELD", THIS_QUARTER);
        var sectionId = section(sessionId);
        slot(sectionId, "Kafka");
        slot(sectionId, "Spring @AutoMvc");

        // when
        var actual = sut.heldInPeriod(ORG, QUARTER_START, QUARTER_END);

        // then
        assertThat(actual).singleElement().satisfies(held -> {
            assertThat(held.sessionId()).isEqualTo(sessionId);
            assertThat(held.talkTitles()).containsExactly("Kafka", "Spring @AutoMvc");
            assertThat(held.talkCount()).isEqualTo(2);
        });
    }

    @Test
    void anUntitledSlotIsCountedAsATalkAndNamedAsNothing() {

        // given
        var guild = community("backend-guild", ORG);
        var sessionId = session(guild, "HELD", THIS_QUARTER);
        var sectionId = section(sessionId);
        slot(sectionId, "Kafka");
        slot(sectionId, null);
        slot(sectionId, "   ");

        // when / then
        assertThat(sut.heldInPeriod(ORG, QUARTER_START, QUARTER_END)).singleElement().satisfies(held -> {
            assertThat(held.talkTitles()).containsExactly("Kafka");
            assertThat(held.talkCount()).isEqualTo(3);
        });
    }

    /**
     * The state {@code talkTitles} is deliberately allowed to reach: an agenda that carried slots and titled none of
     * them. The count says the talks happened and the list names none, which is what the card renders as a session row
     * with a heading and no talk line under it.
     */
    @Test
    void aSessionWhoseEveryTalkIsUntitledNamesNoneOfThemAndStillCountsThemAll() {

        // given
        var guild = community("backend-guild", ORG);
        var sectionId = section(session(guild, "HELD", THIS_QUARTER, ORG, "Open floor"));
        slot(sectionId, null);
        slot(sectionId, "   ");

        // when / then
        assertThat(sut.heldInPeriod(ORG, QUARTER_START, QUARTER_END)).singleElement().satisfies(held -> {
            assertThat(held.talkTitles()).isEmpty();
            assertThat(held.talkCount()).isEqualTo(2);
        });
    }

    /**
     * Agenda order is {@code (section position, slot position)} and not either half alone. The second section is
     * inserted FIRST here, so its rows carry the lower ids: a read that dropped {@code sec.position} from its
     * {@code ORDER BY} would answer the closing lightning talk before the keynote, and nothing else in this class would
     * notice.
     */
    @Test
    void aHeldSessionNamesItsTalksInAgendaOrderAcrossSectionsNotInInsertionOrder() {

        // given
        var guild = community("backend-guild", ORG);
        var sessionId = session(guild, "HELD", THIS_QUARTER);
        var closing = section(sessionId, 1);
        var opening = section(sessionId, 0);
        slot(closing, "Lightning: pgvector");
        slot(opening, "Keynote: hexagons");
        slot(opening, "Deep dive: ports");

        // when
        var actual = sut.heldInPeriod(ORG, QUARTER_START, QUARTER_END);

        // then
        assertThat(actual).singleElement().satisfies(held -> {
            assertThat(held.talkTitles()).containsExactly("Keynote: hexagons", "Deep dive: ports",
                    "Lightning: pgvector");
            assertThat(held.title()).isEqualTo("Keynote: hexagons");
        });
    }

    @Test
    void heldInPeriod_ordersSessionsOldestFirst() {

        // given
        var guild = community("backend-guild", ORG);
        titled(guild, ALSO_THIS_QUARTER, "Second");
        titled(guild, THIS_QUARTER, "First");

        // when
        var actual = sut.heldInPeriod(ORG, QUARTER_START, QUARTER_END);

        // then
        assertThat(actual).extracting(HeldSession::title).containsExactly("First", "Second");
    }

    @Test
    void heldInPeriod_excludesCancelledSkippedAndOutOfWindowOccurrences() {

        // given
        var guild = community("backend-guild", ORG);
        session(guild, "CANCELLED", THIS_QUARTER);
        session(guild, "SKIPPED", ALSO_THIS_QUARTER);
        session(guild, "HELD", LAST_QUARTER);
        session(guild, "SCHEDULED", NEXT_QUARTER);

        // when
        var actual = sut.heldInPeriod(ORG, QUARTER_START, QUARTER_END);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void heldInPeriod_countsAPastOccurrenceNobodyEverClosed() {

        // given a session still SCHEDULED after its date passed: the organizer never wrote it up
        var guild = community("backend-guild", ORG);
        var sectionId = section(session(guild, "SCHEDULED", THIS_QUARTER, ORG, "Never closed"));
        presenter(slot(sectionId), "alice");

        // when
        var actual = sut.heldInPeriod(ORG, QUARTER_START, QUARTER_END);

        // then: it happened, and dropping it would under-report the quarter - the awaiting-close nudge exists
        // precisely because organizers forget this step
        assertThat(actual).extracting(HeldSession::title).containsExactly("Never closed");
        assertThat(sut.firstTimePresenterCount(ORG, QUARTER_START, QUARTER_END)).isEqualTo(1);
    }

    @Test
    void heldInPeriod_titlesASeriesOccurrenceByItsFirstTitledTalk() {

        // given a session with no title of its own, the shape every series occurrence has
        var guild = community("backend-guild", ORG);
        var sectionId = section(session(guild, "HELD", THIS_QUARTER));
        slot(sectionId, "  ");
        slot(sectionId, "Virtual threads in anger");
        slot(sectionId, "Later talk");

        // when
        var actual = sut.heldInPeriod(ORG, QUARTER_START, QUARTER_END);

        // then: a blank title is not a title, and the first real one names the session
        assertThat(actual).singleElement().extracting(HeldSession::title).isEqualTo("Virtual threads in anger");
    }

    @Test
    void heldInPeriod_aSessionWithNothingToNameItCarriesNoTitle() {

        // given
        var guild = community("backend-guild", ORG);
        section(session(guild, "HELD", THIS_QUARTER));

        // when
        var actual = sut.heldInPeriod(ORG, QUARTER_START, QUARTER_END);

        // then: absent, which the renderer has a state for, rather than an empty heading
        assertThat(actual).singleElement().extracting(HeldSession::title).isNull();
    }

    @Test
    void heldInPeriod_aBlankSessionTitleIsAbsentRatherThanAnEmptyHeading() {

        // given a title column that is nullable but not checked against empty, which is how a blank one gets there
        var guild = community("backend-guild", ORG);
        section(session(guild, "HELD", THIS_QUARTER, ORG, "   "));

        // when
        var actual = sut.heldInPeriod(ORG, QUARTER_START, QUARTER_END);

        // then: the same blank-is-absent rule SessionFacts.sessionTitle applies in Java
        assertThat(actual).singleElement().extracting(HeldSession::title).isNull();
    }

    @Test
    void heldInPeriod_namesEachPresenterOnceInAgendaOrder() {

        // given alice opens, bob follows, alice closes
        var guild = community("backend-guild", ORG);
        var sectionId = section(titled(guild, THIS_QUARTER, "Deep dive"));
        presenter(slot(sectionId), "alice");
        presenter(slot(sectionId), "bob");
        presenter(slot(sectionId), "alice");

        // when
        var actual = sut.heldInPeriod(ORG, QUARTER_START, QUARTER_END);

        // then
        assertThat(actual).singleElement().extracting(HeldSession::presenterNames).isEqualTo(List.of("Alice", "Bob"));
    }

    @Test
    void heldInPeriod_dropsAPresenterTheDirectoryCannotName() {

        // given an erased subject's surrogate id and a member whose display name is blank: both real row shapes
        var guild = community("backend-guild", ORG);
        var sectionId = section(titled(guild, THIS_QUARTER, "Deep dive"));
        presenter(slot(sectionId), ERASED);
        presenter(slot(sectionId), NAMELESS);
        presenter(slot(sectionId), "alice");

        // when
        var actual = sut.heldInPeriod(ORG, QUARTER_START, QUARTER_END);

        // then: no identifier reaches the roundup, and an unnameable presenter is simply absent
        assertThat(actual).singleElement().extracting(HeldSession::presenterNames).isEqualTo(List.of("Alice"));
    }

    @Test
    void heldInPeriod_aQuarterWithNoSessionsIsEmpty() {

        // when
        var actual = sut.heldInPeriod(ORG, QUARTER_START, QUARTER_END);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void heldInPeriod_readsOnlyTheGivenOrganization() {

        // given
        var theirs = community("backend-guild", OTHER_ORG);
        presenter(slot(section(session(theirs, "HELD", THIS_QUARTER, OTHER_ORG, "Theirs"))), "alice");

        // when
        var actual = sut.heldInPeriod(ORG, QUARTER_START, QUARTER_END);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void scheduledAfter_namesTheCommunityTheTitleAndTheDate() {

        // given
        var guild = community("backend-guild", ORG);
        var sectionId = section(session(guild, "SCHEDULED", NEXT_QUARTER, ORG, "Next up"));
        presenter(slot(sectionId), "alice");
        slot(sectionId);

        // when
        var actual = sut.scheduledAfter(ORG, QUARTER_END, AFTER_NEXT_QUARTER);

        // then: whether a slot is still open is not this row's question - openTalkCount asks it organization-wide
        assertThat(actual).singleElement().satisfies(upcoming -> {
            assertThat(upcoming.communityId()).isEqualTo(guild);
            assertThat(upcoming.title()).isEqualTo("Next up");
            assertThat(upcoming.scheduledAt()).isEqualTo(NEXT_QUARTER);
        });
    }

    @Test
    void scheduledAfter_ordersSessionsSoonestFirst() {

        // given
        var guild = community("backend-guild", ORG);
        session(guild, "SCHEDULED", AFTER_NEXT_QUARTER, ORG, "Later");
        session(guild, "SCHEDULED", NEXT_QUARTER, ORG, "Sooner");

        // when
        var actual = sut.scheduledAfter(ORG, QUARTER_END, AFTER_NEXT_QUARTER.plusSeconds(1));

        // then
        assertThat(actual).extracting(UpcomingSession::title).containsExactly("Sooner", "Later");
    }

    @Test
    void scheduledAfter_listsOnlyWhatIsStillOnTheCalendarInTheWindow() {

        // given
        var guild = community("backend-guild", ORG);
        session(guild, "CANCELLED", NEXT_QUARTER);
        session(guild, "SKIPPED", NEXT_QUARTER);
        session(guild, "HELD", NEXT_QUARTER);
        session(guild, "SCHEDULED", THIS_QUARTER);

        // when
        var actual = sut.scheduledAfter(ORG, QUARTER_END, AFTER_NEXT_QUARTER);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void scheduledAfter_readsOnlyTheGivenOrganization() {

        // given
        var theirs = community("backend-guild", OTHER_ORG);
        session(theirs, "SCHEDULED", NEXT_QUARTER, OTHER_ORG, "Theirs");

        // when
        var actual = sut.scheduledAfter(ORG, QUARTER_END, AFTER_NEXT_QUARTER);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void anOpenTalkIsOneOnAScheduledSessionWithNobodyPresentingIt() {

        // given
        var guild = community("backend-guild", ORG);
        var sectionId = section(session(guild, "SCHEDULED", NEXT_QUARTER, ORG, "Overdue roundtable"));
        var taken = slot(sectionId, "Event sourcing");
        presenter(taken, "user-1");
        slot(sectionId, "Awaiting a presenter");

        // when / then
        assertThat(sut.openTalkCount(ORG, QUARTER_END, AFTER_NEXT_QUARTER)).isEqualTo(1);
    }

    @Test
    void aCancelledSessionsEmptySlotsAreNotAnInvitation() {

        // given: an empty slot on a session that is not happening is not an invitation
        var guild = community("backend-guild", ORG);
        var cancelled = session(guild, "CANCELLED", NEXT_QUARTER, ORG, "Called off");
        slot(section(cancelled), "Nobody on this");

        // when / then
        assertThat(sut.openTalkCount(ORG, QUARTER_END, AFTER_NEXT_QUARTER)).isZero();
    }

    @Test
    void openTalkCount_readsOnlyTheGivenOrganization() {

        // given
        var theirs = community("backend-guild", OTHER_ORG);
        slot(section(session(theirs, "SCHEDULED", NEXT_QUARTER, OTHER_ORG, "Theirs")), "Nobody on this");

        // when
        var actual = sut.openTalkCount(ORG, QUARTER_END, AFTER_NEXT_QUARTER);

        // then
        assertThat(actual).isZero();
    }

    /**
     * The baseline seeds no reference catalogs, so the rows this test's inserts select must be its own - and every
     * lookup below picks them by organization rather than by {@code min(id)} across the whole table, so a catalog row
     * another test committed cannot become this one's under a different suite order.
     */
    private void seedReferenceCatalogs() {

        seedReferenceCatalogs(ORG);
        seedReferenceCatalogs(OTHER_ORG);
    }

    private void seedReferenceCatalogs(String organizationId) {

        jdbc.update(
                "INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                        + "VALUES (:org, 'Guild', now(), now(), 0) ON CONFLICT DO NOTHING",
                new MapSqlParameterSource("org", organizationId));
        jdbc.update("INSERT INTO event_section_type "
                + "(organization_id, label, expects_presenter, created_at, updated_at, version, icon, accent_color) "
                + "VALUES (:org, 'Talks', true, now(), now(), 0, 'wrench', '#123456') ON CONFLICT DO NOTHING",
                new MapSqlParameterSource("org", organizationId));
    }

    /**
     * Stands in for the real {@link UserProfileAccess}, which resolves ids to names and never answers with an id. It
     * deliberately answers for neither an erased subject's surrogate id nor a member with a blank display name: both
     * are real row shapes, and the adapter must render neither of them as an identifier.
     */
    private static UserProfileAccess directoryThatNames() {

        return new UserProfileAccess() {

            @Override
            public String displayName(String organizationId, String userId) {

                return displayNames(organizationId, List.of(userId)).get(userId);
            }

            @Override
            public Map<String, String> displayNames(String organizationId, Collection<String> userIds) {

                var names = new LinkedHashMap<String, String>();
                for (String userId : userIds) {
                    if (NAMELESS.equals(userId)) {
                        names.put(userId, "  ");
                    } else if (!ERASED.equals(userId)) {
                        names.put(userId, Character.toUpperCase(userId.charAt(0)) + userId.substring(1));
                    }
                }
                return names;
            }

            /** This adapter renders names, never clocks: nothing here reads a zone. */
            @Override
            public Map<String, String> timeZones(String organizationId, Collection<String> userIds) {

                return Map.of();
            }
        };
    }

    // ----- raw-SQL seeding: created_at is auditing-managed, so repositories can't backdate rows -----

    private long community(String slug, String organizationId) {

        return jdbc.queryForObject("""
                INSERT INTO communities (organization_id, slug, name, kind_id, description, status,
                        can_start_series, can_edit_structure, can_queue_topics, created_at, updated_at, version)
                VALUES (:org, :slug, 'Guild',
                        (SELECT min(id) FROM community_kind WHERE organization_id = :org), 'd', 'ACTIVE',
                        false, false, false, now(), now(), 0)
                RETURNING id
                """, new MapSqlParameterSource("slug", slug).addValue("org", organizationId), Long.class);
    }

    private long session(long communityId, String status, Instant scheduledAt) {

        return session(communityId, status, scheduledAt, ORG, null);
    }

    private long titled(long communityId, Instant scheduledAt, String title) {

        return session(communityId, "HELD", scheduledAt, ORG, title);
    }

    private long session(long communityId, String status, Instant scheduledAt, String organizationId, String title) {

        return jdbc.queryForObject("""
                INSERT INTO community_session (organization_id, community_id, status, scheduled_at,
                        title, notes, recording_url, has_time, created_at, updated_at, version)
                VALUES (:org, :communityId, :status, :scheduledAt, :title, '', '', true, now(), now(), 0)
                RETURNING id
                """,
                new MapSqlParameterSource().addValue("communityId", communityId).addValue("status", status)
                        .addValue("scheduledAt", Timestamp.from(scheduledAt)).addValue("org", organizationId)
                        .addValue("title", title),
                Long.class);
    }

    private long section(long sessionId) {

        return jdbc.queryForObject("""
                INSERT INTO event_section (session_id, position, section_type_id, title)
                VALUES (:sessionId, (SELECT count(*) FROM event_section WHERE session_id = :sessionId),
                        (SELECT min(t.id) FROM event_section_type t
                         JOIN community_session s ON s.organization_id = t.organization_id
                         WHERE s.id = :sessionId), 'Talks')
                RETURNING id
                """, new MapSqlParameterSource("sessionId", sessionId), Long.class);
    }

    /** Position stated rather than derived from insertion order, so a test can insert an agenda out of order. */
    private long section(long sessionId, int position) {

        return jdbc.queryForObject("""
                INSERT INTO event_section (session_id, position, section_type_id, title)
                VALUES (:sessionId, :position,
                        (SELECT min(t.id) FROM event_section_type t
                         JOIN community_session s ON s.organization_id = t.organization_id
                         WHERE s.id = :sessionId), 'Talks')
                RETURNING id
                """, new MapSqlParameterSource("sessionId", sessionId).addValue("position", position), Long.class);
    }

    private long slot(long sectionId) {

        return slot(sectionId, null);
    }

    private long slot(long sectionId, String title) {

        return jdbc.queryForObject("""
                INSERT INTO event_slot (section_id, position, title, duration_minutes)
                VALUES (:sectionId, (SELECT count(*) FROM event_slot WHERE section_id = :sectionId), :title, 15)
                RETURNING id
                """, new MapSqlParameterSource("sectionId", sectionId).addValue("title", title), Long.class);
    }

    private void presenter(long slotId, String userId) {

        jdbc.update("INSERT INTO event_slot_presenter (slot_id, user_id) VALUES (:slotId, :userId)",
                new MapSqlParameterSource().addValue("slotId", slotId).addValue("userId", userId));
    }
}
