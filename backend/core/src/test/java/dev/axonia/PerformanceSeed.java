package dev.axonia;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;

import dev.axonia.community.adapter.out.persistence.CommunityEntity;
import dev.axonia.community.adapter.out.persistence.CommunityKindEntity;
import dev.axonia.community.adapter.out.persistence.CommunityKindRepository;
import dev.axonia.community.adapter.out.persistence.CommunityRepository;
import dev.axonia.community.api.CommunityStatus;
import dev.axonia.events.adapter.out.persistence.ActionItemEntity;
import dev.axonia.events.adapter.out.persistence.ActionItemRepository;
import dev.axonia.events.adapter.out.persistence.ActionItemTagRef;
import dev.axonia.events.adapter.out.persistence.AttendanceEntity;
import dev.axonia.events.adapter.out.persistence.AttendanceRepository;
import dev.axonia.events.adapter.out.persistence.SectionEntity;
import dev.axonia.events.adapter.out.persistence.SectionTypeEntity;
import dev.axonia.events.adapter.out.persistence.SectionTypeRepository;
import dev.axonia.events.adapter.out.persistence.SeriesEntity;
import dev.axonia.events.adapter.out.persistence.SeriesRepository;
import dev.axonia.events.adapter.out.persistence.SessionEntity;
import dev.axonia.events.adapter.out.persistence.SessionRepository;
import dev.axonia.events.adapter.out.persistence.SlotEntity;
import dev.axonia.events.adapter.out.persistence.SlotPresenterRef;
import dev.axonia.events.adapter.out.persistence.SlotTagRef;
import dev.axonia.events.adapter.out.persistence.TopicEntity;
import dev.axonia.events.adapter.out.persistence.TopicRepository;
import dev.axonia.events.api.ActionItemOutcome;
import dev.axonia.events.api.TopicKind;
import dev.axonia.events.api.TopicStatus;
import dev.axonia.events.domain.CadenceUnit;
import dev.axonia.events.domain.SeriesStatus;
import dev.axonia.knowledge.adapter.out.persistence.ArtifactEntity;
import dev.axonia.knowledge.adapter.out.persistence.ArtifactRepository;
import dev.axonia.knowledge.adapter.out.persistence.ArtifactTagRef;
import dev.axonia.knowledge.adapter.out.persistence.ArtifactTypeEntity;
import dev.axonia.knowledge.adapter.out.persistence.ArtifactTypeRepository;
import dev.axonia.shared.api.tenancy.OrganizationScope;

/**
 * Seeds a community and a page of sessions for {@code ReadPathQueryBudgetTest}, entirely through the repositories, and
 * entirely inside {@link OrganizationScope#runAs}: outside any transaction, so every repository call borrows and
 * returns its own connection and pays {@code OrganizationAwareDataSource}'s RLS round trips exactly as an unannotated
 * read path does in production. The {@code sessions} table's RLS write policy would otherwise refuse rows minted under
 * no organization scope at all.
 */
@TestComponent
public class PerformanceSeed {

    public static final String ORGANIZATION = "perf-seed-org";
    public static final String SLUG = "perf-seed-community";

    private static final String PRESENTER_USER_ID = "perf-seed-presenter";
    private static final String SLOT_TAG = "perf-seed-tag";
    private static final String OWNER_USER_ID = "perf-seed-owner";
    private static final String ATTENDEE_USER_ID = "perf-seed-attendee";

    @Autowired
    private CommunityKindRepository communityKindRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private SectionTypeRepository sectionTypeRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SeriesRepository seriesRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private ArtifactTypeRepository artifactTypeRepository;

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private ActionItemRepository actionItemRepository;

    /**
     * Inserts one community and {@code sessionCount} already-held sessions, each with {@code sectionsPerSession}
     * sections of {@code slotsPerSection} presented slots apiece (one presenter, one tag per slot) - a page of the
     * community's own past-sessions read.
     */
    public void sessions(int sessionCount, int sectionsPerSession, int slotsPerSection) {

        OrganizationScope.runAs(ORGANIZATION, () -> {
            var communityId = communityId();
            var sectionType = sectionTypeRepository.save(SectionTypeEntity.builder().organizationId(ORGANIZATION)
                    .label("Talk").expectsPresenter(true).build());

            var now = Instant.now();
            for (var s = 0; s < sessionCount; s++) {
                var scheduledAt = now.minusSeconds(3600L * (s + 1));
                sessionRepository.save(SessionEntity.builder().organizationId(ORGANIZATION).communityId(communityId)
                        .status("HELD").scheduledAt(scheduledAt).hasTime(true).notes("").recordingUrl("")
                        .sections(sections(sectionType.getId(), sectionsPerSession, slotsPerSection)).build());
            }
        });
    }

    /**
     * One session with every optional field a session can carry populated: a series (so the standing place and the
     * series zone are both resolved), a title, an occurrence date, a facilitator, notes, a recording URL, all three
     * parts of its own meeting place, two sections of <em>different</em> types, slots carrying a topic, presenters and
     * tags, and an attendance row. Returns its id.
     *
     * <p>
     * A hand-written read that quietly drops one field is the whole risk of reading a page level by level rather than
     * aggregate by aggregate, and it would pass every budget assertion in this class. So there has to be a session with
     * something in every column to drop, read both ways and compared field by field.
     *
     * <p>
     * Scheduled half an hour ago - later than every session {@link #sessions} seeds, which start an hour back - so it
     * lands on the first page of the community's past sessions rather than off the end of it: the budget assertions
     * then pay for the series, topic and attendance lookups this row alone makes the page need.
     */
    public Long oneFullyPopulatedSession() {

        return OrganizationScope.runAs(ORGANIZATION, () -> {
            var communityId = communityId();
            var series = seriesRepository.save(SeriesEntity.builder().organizationId(ORGANIZATION)
                    .communityId(communityId).title("Performance Series").cadenceCount(1).cadenceUnit(CadenceUnit.WEEK)
                    .startsOn(LocalDate.parse("2026-06-03")).preferredTime(LocalTime.of(18, 0)).timeZone("Europe/Paris")
                    .durationMinutes(60).status(SeriesStatus.ACTIVE).location("Standing room")
                    .joinUrl("https://example.com/standing").joiningDetails("standing code").defaultStructure(List.of())
                    .build());
            var topic = topicRepository.save(TopicEntity.builder().organizationId(ORGANIZATION).communityId(communityId)
                    .kind(TopicKind.REQUEST).title("Write the runbook").description("")
                    .proposerUserId(PRESENTER_USER_ID).status(TopicStatus.SCHEDULED).tags(new HashSet<>()).build());
            var saved = sessionRepository.save(SessionEntity.builder().organizationId(ORGANIZATION)
                    .seriesId(series.getId()).communityId(communityId).status("HELD")
                    .scheduledAt(Instant.now().minusSeconds(1800)).hasTime(true)
                    .occurrenceDate(LocalDate.parse("2026-06-03")).title("Everything, populated").durationMinutes(90)
                    .facilitatorUserId(PRESENTER_USER_ID).notes("what happened")
                    .recordingUrl("https://example.com/recording").location("Room 42")
                    .joinUrl("https://example.com/join").joiningDetails("passcode 1234")
                    .closedAt(Instant.now().minusSeconds(900)).wrapUpReleaseAt(Instant.now().minusSeconds(600))
                    .upcomingReleaseAt(Instant.now().minusSeconds(2400)).sections(fullSections(topic.getId())).build());
            attendanceRepository.save(AttendanceEntity.builder().organizationId(ORGANIZATION).sessionId(saved.getId())
                    .userId(ATTENDEE_USER_ID).build());
            return saved.getId();
        });
    }

    /**
     * Two sections of deliberately different types - a page that resolved one type for every section would still look
     * right with one - the first holding a slot filled from a topic, the second a slot carrying its own subject.
     */
    private List<SectionEntity> fullSections(Long topicId) {

        var deepDive = sectionTypeRepository.save(SectionTypeEntity.builder().organizationId(ORGANIZATION)
                .label("Deep dive").expectsPresenter(true).build());
        var retro = sectionTypeRepository.save(SectionTypeEntity.builder().organizationId(ORGANIZATION).label("Retro")
                .expectsPresenter(false).build());
        var fromTopic = SlotEntity.builder().topicId(topicId).title("Runbook").notes("carried over").durationMinutes(20)
                .presenters(new HashSet<>(List.of(SlotPresenterRef.builder().userId(PRESENTER_USER_ID).build(),
                        SlotPresenterRef.builder().userId(OWNER_USER_ID).build())))
                .tags(new HashSet<>()).build();
        var ownSubject = SlotEntity.builder().title("Open floor").notes(null).durationMinutes(10)
                .presenters(new HashSet<>(List.of(SlotPresenterRef.builder().userId(ATTENDEE_USER_ID).build())))
                .tags(new HashSet<>(List.of(SlotTagRef.builder().tag(SLOT_TAG).build(),
                        SlotTagRef.builder().tag("perf-seed-other").build())))
                .build();
        return List.of(
                SectionEntity.builder().sectionTypeId(deepDive.getId()).title("Deep dive").durationMinutes(60)
                        .notes("section notes").slots(new ArrayList<>(List.of(fromTopic))).build(),
                SectionEntity.builder().sectionTypeId(retro.getId()).title("Retro").durationMinutes(30).notes(null)
                        .slots(new ArrayList<>(List.of(ownSubject))).build());
    }

    /**
     * One upcoming one-off session - no series - carrying a zone of its own, three days out so it is never the
     * community's soonest upcoming session the action-item checkpoints resolve against. Returns its id.
     */
    public Long oneOffSessionWithItsOwnZone() {

        return OrganizationScope.runAs(ORGANIZATION,
                () -> sessionRepository.save(SessionEntity.builder().organizationId(ORGANIZATION)
                        .communityId(communityId()).status("SCHEDULED").scheduledAt(Instant.now().plusSeconds(259_200))
                        .hasTime(true).timeZone("Europe/Zurich").title("One-off").notes("").recordingUrl("")
                        .sections(List.of()).build()).getId());
    }

    /**
     * Inserts {@code artifactCount} already-active artifacts, each carrying one tag - a page of the community's own
     * artifact library search. Runs inside {@link OrganizationScope#runAs} for the same reason {@link #sessions} does:
     * outside any transaction, so the write side pays exactly what an unannotated write path pays in production, and
     * because the {@code artifact} table's RLS write policy refuses rows minted under no organization scope at all.
     */
    public void artifacts(int artifactCount) {

        OrganizationScope.runAs(ORGANIZATION, () -> {
            var communityId = communityId();
            var type = artifactTypeRepository
                    .save(ArtifactTypeEntity.builder().organizationId(ORGANIZATION).label("Performance").build());

            for (var a = 0; a < artifactCount; a++) {
                artifactRepository.save(ArtifactEntity.builder().organizationId(ORGANIZATION).communityId(communityId)
                        .typeId(type.getId()).title("Artifact " + a).description("Performance seeded artifact")
                        .url("https://example.com/artifact/" + a).contributorUserId(PRESENTER_USER_ID).status("ACTIVE")
                        .searchDocument("Artifact " + a)
                        .tags(new HashSet<>(List.of(ArtifactTagRef.builder().tag(SLOT_TAG).build()))).build());
            }
        });
    }

    /**
     * Inserts {@code itemCount} already-open action items, each carrying one tag - a page of the community's own
     * action-items tab. Runs inside {@link OrganizationScope#runAs} for the same reason {@link #sessions} and
     * {@link #artifacts} do.
     *
     * <p>
     * Every other item is pinned to a checkpoint session and the rest are left late-bound, so both arms of
     * {@code CheckpointResolver.resolveAll} are on every page whatever its size. Before those sessions existed every
     * session here was {@code HELD} and in the past, so the resolution the budget measures resolved to nothing at all
     * and a per-row session or series lookup would have passed it unnoticed.
     */
    public void actionItems(int itemCount) {

        OrganizationScope.runAs(ORGANIZATION, () -> {
            var communityId = communityId();
            var pinned = pinnedCheckpointSession(communityId);
            for (var a = 0; a < itemCount; a++) {
                actionItemRepository.save(ActionItemEntity.builder().organizationId(ORGANIZATION)
                        .communityId(communityId).title("Action item " + a)
                        .description("Performance seeded action item").raisedByUserId(PRESENTER_USER_ID)
                        .checkpointSessionId(a % 2 == 0 ? pinned : null).outcome(ActionItemOutcome.OPEN)
                        .tags(new HashSet<>(List.of(ActionItemTagRef.builder().tag(SLOT_TAG).build()))).build());
            }
        });
    }

    /**
     * Two upcoming sessions in two series in two zones: the later one, returned here, is what the pinned items name,
     * and the earlier one is what the late-bound ones resolve to - so a page's rows genuinely resolve to different
     * sessions with different governing zones.
     *
     * <p>
     * One pinned session rather than several, deliberately. {@code findByOrganizationIdAndCommunityIdAndIdIn} returns
     * whole {@code SessionEntity} aggregates, so Spring Data JDBC reads each one's {@code sections} collection with a
     * statement of its own, and a page naming several distinct pinned sessions would then cost more the more of them it
     * happens to show - which the budget's "must not grow" assertion would report as a regression in this read.
     */
    private Long pinnedCheckpointSession(Long communityId) {

        upcomingSession(communityId, "Asia/Tokyo", 3600L);
        return upcomingSession(communityId, "Europe/Paris", 7200L);
    }

    private Long upcomingSession(Long communityId, String zone, long secondsFromNow) {

        var series = seriesRepository.save(SeriesEntity.builder().organizationId(ORGANIZATION).communityId(communityId)
                .title("Checkpoint series " + zone).cadenceCount(1).cadenceUnit(CadenceUnit.WEEK)
                .startsOn(LocalDate.parse("2026-06-03")).preferredTime(LocalTime.of(18, 0)).timeZone(zone)
                .status(SeriesStatus.ACTIVE).defaultStructure(List.of()).build());
        return sessionRepository.save(SessionEntity.builder().organizationId(ORGANIZATION).seriesId(series.getId())
                .communityId(communityId).status("SCHEDULED").scheduledAt(Instant.now().plusSeconds(secondsFromNow))
                .hasTime(true).occurrenceDate(LocalDate.parse("2026-06-03")).notes("").recordingUrl("")
                .sections(List.of()).build()).getId();
    }

    /**
     * Finds the shared performance-seed community, creating it on the first call. {@link #sessions} and
     * {@link #artifacts} can run in either order within the same test class, so this makes both idempotent against the
     * one community rather than each minting its own.
     */
    private Long communityId() {

        return communityRepository.findByOrganizationIdAndSlug(ORGANIZATION, SLUG).map(CommunityEntity::getId)
                .orElseGet(() -> {
                    var kind = communityKindRepository.save(
                            CommunityKindEntity.builder().organizationId(ORGANIZATION).label("Performance").build());
                    return communityRepository.save(
                            CommunityEntity.builder().organizationId(ORGANIZATION).slug(SLUG).name("Performance Seed")
                                    .kindId(kind.getId()).description("").status(CommunityStatus.ACTIVE).build())
                            .getId();
                });
    }

    private List<SectionEntity> sections(Long sectionTypeId, int sectionsPerSession, int slotsPerSection) {

        var sections = new ArrayList<SectionEntity>(sectionsPerSession);
        for (var sec = 0; sec < sectionsPerSession; sec++) {
            sections.add(SectionEntity.builder().sectionTypeId(sectionTypeId).title("Section " + sec)
                    .durationMinutes(30).slots(slots(slotsPerSection)).build());
        }
        return sections;
    }

    private List<SlotEntity> slots(int slotsPerSection) {

        var slots = new ArrayList<SlotEntity>(slotsPerSection);
        for (var sl = 0; sl < slotsPerSection; sl++) {
            slots.add(SlotEntity.builder().title("Slot " + sl).durationMinutes(15)
                    .presenters(new HashSet<>(List.of(SlotPresenterRef.builder().userId(PRESENTER_USER_ID).build())))
                    .tags(new HashSet<>(List.of(SlotTagRef.builder().tag(SLOT_TAG).build()))).build());
        }
        return slots;
    }
}
