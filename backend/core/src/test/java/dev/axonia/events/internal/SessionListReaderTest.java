package dev.axonia.events.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.communication.MessageOutcomePort;
import dev.axonia.events.CommunityAccess;
import dev.axonia.events.Section;
import dev.axonia.events.Session;
import dev.axonia.events.SessionStatus;
import dev.axonia.events.Slot;
import dev.axonia.events.mother.SeriesEntityMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The window and hydration contract of {@link SessionListReader}, against a real database.
 *
 * <p>
 * These are the assertions {@code SessionRepositoryTest} used to make about {@code findPast}, {@code findUpcoming} and
 * {@code findBetween}, moved rather than dropped: the list paths read through this class now, so this is where the
 * three windows have to be pinned. The hydration cases beside them are new, because the aggregate used to do that part
 * and now this class does: a session with no sections, a section with no slots, and a slot with neither presenters nor
 * tags are each a real page, and each one is an {@code IN ()} - a syntax error in Postgres - if the guard above it is
 * ever removed.
 *
 * <p>
 * {@link CommunityAccess} and {@link MessageOutcomePort} are mocks because {@link SessionMapper#toSessions} touches
 * neither: the roster and the outbox are the single-session detail read's cost, never a list page's. If a change ever
 * makes a list page read either, these mocks answer null and the test fails loudly rather than quietly paying for it.
 */
class SessionListReaderTest extends CoreJdbcTestSupport {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SeriesRepository seriesRepository;

    @Autowired
    private SectionTypeRepository sectionTypeRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    @Autowired
    private JdbcTemplate jdbc;

    private SessionListReader sut;

    private static final Instant NOW = Instant.parse("2026-06-08T00:00:00Z");

    @BeforeEach
    void wire() {

        sut = new SessionListReader(namedJdbc,
                new SessionMapper(mock(CommunityAccess.class), mock(MessageOutcomePort.class),
                        new SessionBatchLookups(sectionTypeRepository, attendanceRepository, seriesRepository)));
    }

    @Test
    void past_returnsNonCancelledBeforeNowNewestFirst() {

        var communityId = communityId();
        var seriesId = seriesId(communityId);
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2026-06-01T10:00:00Z")); // held, counts
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2026-05-01T10:00:00Z")); // older held, counts
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2099-01-01T10:00:00Z")); // upcoming, excluded
        save(seriesId, communityId, "CANCELLED", Instant.parse("2026-05-15T10:00:00Z")); // cancelled, excluded

        var actual = sut.page(past(communityId).pageable(PageRequest.of(0, 20)).build());

        assertThat(actual).extracting(Session::scheduledAt).containsExactly(Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-05-01T10:00:00Z"));
    }

    /**
     * A past page is every non-cancelled occurrence whose hour has passed, which is not the same set as the sessions
     * that actually took place: an occurrence the community skipped, and one nobody ever confirmed either way, both sit
     * in it. A caller that speaks about what a community did - the overview's marquee, the pulse's reading of the last
     * session - has to be able to ask for the held ones alone rather than trust the newest row to be one.
     */
    @Test
    void past_narrowsToTheAskedStatus() {

        var communityId = communityId();
        var seriesId = seriesId(communityId);
        save(seriesId, communityId, "HELD", Instant.parse("2026-06-01T10:00:00Z")); // held, counts
        save(seriesId, communityId, "SKIPPED", Instant.parse("2026-06-05T10:00:00Z")); // newer, never happened
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2026-06-06T10:00:00Z")); // newer, unconfirmed

        var actual = sut.page(past(communityId).status(SessionStatus.HELD).pageable(PageRequest.of(0, 20)).build());

        assertThat(actual).extracting(Session::scheduledAt).containsExactly(Instant.parse("2026-06-01T10:00:00Z"));
    }

    /**
     * The one predicate in {@code SessionListReader.ROOTS_SQL} nothing else here exercises. That class's javadoc argues
     * the tenant scoping "has to be right by construction rather than by testing" because Testcontainers connects as
     * the schema owner and so bypasses RLS by ownership - true of the RLS <em>policy</em>, and false of the explicit
     * {@code organization_id = :organizationId} beside it, which a second organization's row proves or disproves with
     * RLS playing no part at all. {@code ArtifactRepositoryTest} already reads that way; every other test in this file
     * asks for {@code "default"} and would pass just as happily with the predicate deleted.
     *
     * <p>
     * The intruder sits in the same community as the row that must come back, and inside the same window: only the
     * organization differs, so nothing but that predicate can exclude it. {@code community_session.community_id}
     * references {@code communities(id)} alone rather than the pair, which is what lets a row be written this way.
     */
    @Test
    void past_doesNotReturnAnotherOrganizationsSessionInTheSameCommunity() {

        var communityId = communityId();
        save(null, communityId, "SCHEDULED", Instant.parse("2026-06-01T10:00:00Z"));
        sessionRepository
                .save(SessionEntity.builder().organizationId("acme").communityId(communityId).status("SCHEDULED")
                        .scheduledAt(Instant.parse("2026-06-02T10:00:00Z")).occurrenceDate(LocalDate.of(2026, 6, 2))
                        .notes("").recordingUrl("").sections(new ArrayList<>()).build());

        var actual = sut.page(past(communityId).pageable(PageRequest.of(0, 20)).build());

        assertThat(actual).extracting(Session::scheduledAt).containsExactly(Instant.parse("2026-06-01T10:00:00Z"));
    }

    @Test
    void past_honoursFromToWindow() {

        var communityId = communityId();
        var seriesId = seriesId(communityId);
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2026-03-01T10:00:00Z")); // before window
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2026-04-15T10:00:00Z")); // inside window
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2026-06-01T10:00:00Z")); // after window

        var actual = sut.page(past(communityId).from(Instant.parse("2026-04-01T00:00:00Z"))
                .to(Instant.parse("2026-05-01T00:00:00Z")).pageable(PageRequest.of(0, 20)).build());

        // from inclusive, to exclusive
        assertThat(actual).extracting(Session::scheduledAt).containsExactly(Instant.parse("2026-04-15T10:00:00Z"));
    }

    @Test
    void past_paginates() {

        var communityId = communityId();
        var seriesId = seriesId(communityId);
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2026-06-01T10:00:00Z"));
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2026-05-01T10:00:00Z"));

        var first = sut.page(past(communityId).pageable(PageRequest.of(0, 1)).build());
        var second = sut.page(past(communityId).pageable(PageRequest.of(1, 1)).build());

        assertThat(first).extracting(Session::scheduledAt).containsExactly(Instant.parse("2026-06-01T10:00:00Z"));
        assertThat(second).extracting(Session::scheduledAt).containsExactly(Instant.parse("2026-05-01T10:00:00Z"));
    }

    @Test
    void upcoming_returnsFutureNonCancelledOnlyEarliestFirst() {

        var communityId = communityId();
        var seriesId = seriesId(communityId);
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2099-02-01T10:00:00Z")); // future, counts
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2099-01-01T10:00:00Z")); // future, counts, earlier
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2000-01-01T10:00:00Z")); // past, excluded
        save(seriesId, communityId, "CANCELLED", Instant.parse("2099-03-01T10:00:00Z")); // cancelled, excluded

        var actual = sut.page(SessionPageQuery.builder().organizationId("default").communityId(communityId).now(NOW)
                .window(SessionPageQuery.Window.UPCOMING).pageable(PageRequest.of(0, 20)).build());

        assertThat(actual).extracting(Session::scheduledAt).containsExactly(Instant.parse("2099-01-01T10:00:00Z"),
                Instant.parse("2099-02-01T10:00:00Z"));
    }

    @Test
    void between_returnsEveryStatusInTheWindowOldestFirst() {

        var communityId = communityId();
        var seriesId = seriesId(communityId);
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2026-06-15T10:00:00Z")); // inside, counts
        save(seriesId, communityId, "CANCELLED", Instant.parse("2026-06-20T10:00:00Z")); // inside, still counts
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2026-07-01T10:00:00Z")); // equals to, excluded
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2026-05-31T10:00:00Z")); // before window, excluded

        var actual = sut.page(between(communityId));

        assertThat(actual).extracting(Session::scheduledAt).containsExactly(Instant.parse("2026-06-15T10:00:00Z"),
                Instant.parse("2026-06-20T10:00:00Z"));
    }

    /**
     * The whole four-level tree, read once per level: the ordering the aggregate got from
     * {@code @MappedCollection(keyColumn = "position")} is restated by this reader's own {@code ORDER BY}, so a section
     * or slot arriving in the wrong place is a defect only this assertion can see.
     *
     * <p>
     * The stored positions are reversed after the insert, deliberately. Rows written through the aggregate come out
     * with their ids ascending in step with their positions, so a fixture that leaves them that way passes just as
     * happily against {@code ORDER BY id} and pins nothing at all: the sections and slots here read back in the
     * opposite order to the one they were written in, which only an {@code ORDER BY "position"} can produce.
     */
    @Test
    void readsSectionsSlotsPresentersAndTagsInPositionOrder() {

        var communityId = communityId();
        var seriesId = seriesId(communityId);
        var typeId = sectionTypeId();
        var sections = List.of(section(typeId, "Opening", List.of(slot("Welcome", Set.of("ana"), Set.of("kickoff")))),
                section(typeId, "Talks", List.of(slot("Kafka", Set.of("bob", "ana"), Set.of("streams", "kafka")),
                        slot("Postgres", Set.of("cleo"), Set.of("sql")))));
        var sessionId = saveWithSections(seriesId, communityId, Instant.parse("2026-06-01T10:00:00Z"), sections);
        reverseStoredPositions(sessionId);

        var actual = sut.page(past(communityId).pageable(PageRequest.of(0, 20)).build());

        assertThat(actual).singleElement().satisfies(session -> {
            assertThat(session.sections()).extracting(Section::title).containsExactly("Talks", "Opening");
            assertThat(session.sections()).extracting(Section::position).containsExactly(0, 1);
            assertThat(session.sections().get(0).slots()).extracting(Slot::title).containsExactly("Postgres", "Kafka");
            assertThat(session.sections().get(0).slots()).extracting(Slot::position).containsExactly(0, 1);
            assertThat(session.sections().get(0).slots().get(1).presenterUserIds()).containsExactly("ana", "bob");
            assertThat(session.sections().get(0).slots().get(1).tags()).containsExactly("kafka", "streams");
        });
    }

    /**
     * {@code scheduled_at} is not unique, so paging on it alone lets Postgres answer two ties in a different order for
     * each {@code OFFSET} - repeating one session on two consecutive pages and dropping another. The reader breaks the
     * tie on {@code id}, running the same way as the sort, so the three sessions below page out newest id first and
     * every one of them is seen exactly once.
     */
    @Test
    void past_pagesDeterministicallyAcrossSessionsSharingAnInstant() {

        // Three one-off sessions rather than three occurrences of one series: uq_community_session_organization_
        // _series_occurrence would refuse the latter, and two one-offs at the same hour is the real shape of this
        // anyway.
        var communityId = communityId();
        var sameInstant = Instant.parse("2026-06-01T10:00:00Z");
        var first = save(null, communityId, "SCHEDULED", sameInstant);
        var second = save(null, communityId, "SCHEDULED", sameInstant);
        var third = save(null, communityId, "SCHEDULED", sameInstant);

        var pageOne = sut.page(past(communityId).pageable(PageRequest.of(0, 2)).build());
        var pageTwo = sut.page(past(communityId).pageable(PageRequest.of(1, 2)).build());

        var paged = Stream.concat(pageOne.stream(), pageTwo.stream()).map(Session::id).toList();
        assertThat(paged).containsExactly(third, second, first);
    }

    /**
     * An unpaged request reads the whole window rather than throwing. {@link Pageable#unpaged()} is a legal value of
     * the parameter's own type - {@code SessionService} hands this reader whatever its caller passed - and Spring
     * Data's {@code Unpaged} throws from {@code getPageSize()}, so without the guard in {@code appendPage} a legal
     * argument would become an exception on a read path. Line coverage alone would never notice: the guard shares its
     * line with the null case the schedule's own window already exercises.
     */
    @Test
    void anUnpagedRequestReadsTheWholeWindow() {

        var communityId = communityId();
        var seriesId = seriesId(communityId);
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2026-06-01T10:00:00Z"));
        save(seriesId, communityId, "SCHEDULED", Instant.parse("2026-05-01T10:00:00Z"));

        assertThat(sut.page(past(communityId).pageable(Pageable.unpaged()).build())).hasSize(2);
    }

    /**
     * A one-off session with no agenda at all: the guard that would otherwise compose {@code IN ()} against
     * {@code event_section}. The same case one level down - a section with no slots - is covered below.
     */
    @Test
    void aSessionWithNoSectionsReadsAsAnEmptyAgenda() {

        var communityId = communityId();
        save(seriesId(communityId), communityId, "SCHEDULED", Instant.parse("2026-06-01T10:00:00Z"));

        var actual = sut.page(past(communityId).pageable(PageRequest.of(0, 20)).build());

        assertThat(actual).singleElement().satisfies(session -> assertThat(session.sections()).isEmpty());
    }

    @Test
    void aSectionWithNoSlotsReadsAsAnEmptySection() {

        var communityId = communityId();
        saveWithSections(seriesId(communityId), communityId, Instant.parse("2026-06-01T10:00:00Z"),
                List.of(section(sectionTypeId(), "Opening", List.of())));

        var actual = sut.page(past(communityId).pageable(PageRequest.of(0, 20)).build());

        assertThat(actual).singleElement().satisfies(session -> assertThat(session.sections()).singleElement()
                .satisfies(section -> assertThat(section.slots()).isEmpty()));
    }

    @Test
    void aSlotWithNoPresentersOrTagsReadsAsBothEmpty() {

        var communityId = communityId();
        saveWithSections(seriesId(communityId), communityId, Instant.parse("2026-06-01T10:00:00Z"),
                List.of(section(sectionTypeId(), "Opening", List.of(slot("Welcome", Set.of(), Set.of())))));

        var actual = sut.page(past(communityId).pageable(PageRequest.of(0, 20)).build());

        assertThat(actual.getFirst().sections().getFirst().slots()).singleElement().satisfies(slot -> {
            assertThat(slot.presenterUserIds()).isEmpty();
            assertThat(slot.tags()).isEmpty();
        });
    }

    /** An empty page never reaches a child level at all, so it never composes an {@code IN ()} against one. */
    @Test
    void anEmptyWindowReadsAsAnEmptyPage() {

        assertThat(sut.page(past(communityId()).pageable(PageRequest.of(0, 20)).build())).isEmpty();
    }

    @Test
    void carriesTheRowsOwnColumnsThroughToTheDto() {

        var communityId = communityId();
        var seriesId = seriesId(communityId);
        var saved = sessionRepository.save(SessionEntity.builder().organizationId("default").seriesId(seriesId)
                .communityId(communityId).status("HELD").scheduledAt(Instant.parse("2026-06-01T10:00:00Z"))
                .hasTime(false).occurrenceDate(LocalDate.of(2026, 6, 1)).title("Retro").durationMinutes(45)
                .facilitatorUserId("ana").notes("what happened").recordingUrl("https://example.com/rec")
                .location("Room 3").joinUrl("https://meet.example/room").joiningDetails("code 1234")
                .closedAt(Instant.parse("2026-06-01T12:00:00Z")).sections(List.of()).build());
        var stored = sessionRepository.findById(saved.getId()).orElseThrow();

        var actual = sut.page(past(communityId).pageable(PageRequest.of(0, 20)).build());

        assertThat(actual).singleElement().satisfies(session -> {
            assertThat(session.id()).isEqualTo(saved.getId());
            assertThat(session.seriesId()).isEqualTo(seriesId);
            assertThat(session.communityId()).isEqualTo(communityId);
            assertThat(session.status()).isEqualTo(SessionStatus.HELD);
            assertThat(session.title()).isEqualTo("Retro");
            assertThat(session.occurrenceDate()).isEqualTo(LocalDate.of(2026, 6, 1));
            assertThat(session.hasTime()).isFalse();
            assertThat(session.durationMinutes()).isEqualTo(45);
            assertThat(session.facilitatorUserId()).isEqualTo("ana");
            assertThat(session.notes()).isEqualTo("what happened");
            assertThat(session.recordingUrl()).isEqualTo("https://example.com/rec");
            assertThat(session.place().location()).isEqualTo("Room 3");
            assertThat(session.place().joinUrl()).isEqualTo("https://meet.example/room");
            assertThat(session.place().joiningDetails()).isEqualTo("code 1234");
            assertThat(session.closedAt()).isEqualTo(Instant.parse("2026-06-01T12:00:00Z"));
            assertThat(session.createdAt()).isEqualTo(stored.getCreatedAt());
            assertThat(session.updatedAt()).isEqualTo(stored.getUpdatedAt());
            assertThat(session.version()).isEqualTo(stored.getVersion());
        });
    }

    private SessionPageQuery.SessionPageQueryBuilder past(Long communityId) {

        return SessionPageQuery.builder().organizationId("default").communityId(communityId).now(NOW)
                .window(SessionPageQuery.Window.PAST);
    }

    private SessionPageQuery between(Long communityId) {

        return SessionPageQuery.builder().organizationId("default").communityId(communityId)
                .from(Instant.parse("2026-06-01T00:00:00Z")).to(Instant.parse("2026-07-01T00:00:00Z"))
                .window(SessionPageQuery.Window.BETWEEN).build();
    }

    private SectionEntity section(Long sectionTypeId, String title, List<SlotEntity> slots) {

        return SectionEntity.builder().sectionTypeId(sectionTypeId).title(title).durationMinutes(30).notes(null)
                .slots(new ArrayList<>(slots)).build();
    }

    private SlotEntity slot(String title, Set<String> presenters, Set<String> tags) {

        return SlotEntity.builder().title(title).notes(null).durationMinutes(15)
                .presenters(presenters.stream().map(u -> SlotPresenterRef.builder().userId(u).build())
                        .collect(Collectors.toCollection(HashSet::new)))
                .tags(tags.stream().map(t -> SlotTagRef.builder().tag(t).build())
                        .collect(Collectors.toCollection(HashSet::new)))
                .build();
    }

    private Long save(Long seriesId, Long communityId, String status, Instant at) {

        return saveWithSections(seriesId, communityId, at, List.of(), status);
    }

    private Long saveWithSections(Long seriesId, Long communityId, Instant at, List<SectionEntity> sections) {

        return saveWithSections(seriesId, communityId, at, sections, "SCHEDULED");
    }

    private Long saveWithSections(Long seriesId, Long communityId, Instant at, List<SectionEntity> sections,
            String status) {

        return sessionRepository
                .save(SessionEntity.builder().organizationId("default").seriesId(seriesId).communityId(communityId)
                        .status(status).scheduledAt(at).occurrenceDate(at.atOffset(ZoneOffset.UTC).toLocalDate())
                        .notes("").recordingUrl("").sections(new ArrayList<>(sections)).build())
                .getId();
    }

    /**
     * Reverses the stored {@code position} of a session's two sections, and of the two slots of its {@code Talks}
     * section, so that neither reads back in id order any more. Done in two statements rather than one because
     * {@code uq_event_section_session_position} and {@code uq_event_slot_section_position} are checked row by row: the
     * first parks both rows out of the way, the second brings them back swapped.
     */
    private void reverseStoredPositions(Long sessionId) {

        jdbc.update("UPDATE event_section SET \"position\" = \"position\" + 10 WHERE session_id = ?", sessionId);
        jdbc.update("UPDATE event_section SET \"position\" = 11 - \"position\" WHERE session_id = ?", sessionId);
        var talks = "(SELECT id FROM event_section WHERE session_id = ? AND title = 'Talks')";
        jdbc.update("UPDATE event_slot SET \"position\" = \"position\" + 10 WHERE section_id = " + talks, sessionId);
        jdbc.update("UPDATE event_slot SET \"position\" = 11 - \"position\" WHERE section_id = " + talks, sessionId);
    }

    private Long sectionTypeId() {

        return sectionTypeRepository.save(
                SectionTypeEntity.builder().organizationId("default").label("Talk").expectsPresenter(true).build())
                .getId();
    }

    private Long seriesId(Long communityId) {

        return seriesRepository.save(SeriesEntityMother.any().communityId(communityId)
                .startsOn(LocalDate.parse("2026-06-03")).defaultStructure(List.of()).build()).getId();
    }

    private Long communityId() {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES ('default', 'GuildL', now(), now(), 0) ON CONFLICT DO NOTHING");
        return jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, updated_at, version) "
                        + "SELECT 'default','list-' || nextval('communities_id_seq'),'L', k.id,'','ACTIVE', now(), now(), 0 "
                        + "FROM community_kind k WHERE k.organization_id='default' AND k.label='GuildL' RETURNING id",
                Long.class);
    }
}
