package dev.axonia.events.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.FootprintTestSupport;
import dev.axonia.MemberErasure;

import static org.assertj.core.api.Assertions.assertThat;

class EventsMemberDataEraserTest extends FootprintTestSupport {

    private static final String ORG = "default";
    private static final String OTHER_ORG = "other-org";
    private static final String TARGET = "target-user";
    private static final String NEIGHBOUR = "neighbour-user";
    private static final String SURROGATE = "erased:test-surrogate";
    private static final Instant ERASED_AT = Instant.parse("2026-07-27T10:00:00Z");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private EventsMemberDataEraser eraser;

    private final Map<Long, Long> slotBySession = new HashMap<>();

    private Long communityId;
    private Long sectionTypeId;

    @BeforeEach
    void seedFixtures() {

        communityId = seedCommunity(ORG);
        sectionTypeId = seedSectionType(ORG);
    }

    /**
     * Both halves of the split write to {@code @Version} entities, so both must invalidate any version a concurrent
     * request is already holding: a cleared future commitment that left the version alone would be written straight
     * back, subject and all, by whoever loaded the session first.
     */
    @Test
    void bothTheClearedAndTheAnonymisedRowAreVersionBumpedAndStamped() {

        var pastSession = seedSession(TARGET, Instant.parse("2026-01-01T10:00:00Z"));
        var futureSession = seedSession(TARGET, Instant.parse("2026-12-01T10:00:00Z"));

        eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET).surrogateId(SURROGATE)
                .erasedAt(ERASED_AT).build());

        assertThat(sessionAudit(pastSession)).containsEntry("version", 1L).containsEntry("updated_at",
                Timestamp.from(ERASED_AT));
        assertThat(sessionAudit(futureSession)).containsEntry("version", 1L).containsEntry("updated_at",
                Timestamp.from(ERASED_AT));
    }

    private Map<String, Object> sessionAudit(Long sessionId) {

        return jdbc.queryForMap("SELECT version, updated_at FROM community_session WHERE id = :id",
                Map.of("id", sessionId));
    }

    @Test
    void keepsPastAttributionsAndClearsFutureCommitments() {

        var pastSession = seedSession(TARGET, Instant.parse("2026-01-01T10:00:00Z"));
        var futureSession = seedSession(TARGET, Instant.parse("2026-12-01T10:00:00Z"));
        seedPresenter(TARGET, slotOf(pastSession));
        seedPresenter(TARGET, slotOf(futureSession));
        // a neighbour presenter on each slot: the future DELETE and the past anonymise are each scoped by
        // "AND user_id = :user", and only a by-value assertion on a row that survives either way would notice
        // that predicate being dropped
        seedPresenter(NEIGHBOUR, slotOf(pastSession));
        seedPresenter(NEIGHBOUR, slotOf(futureSession));

        var counts = eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET).surrogateId(SURROGATE)
                .erasedAt(ERASED_AT).build());

        assertThat(facilitatorOf(pastSession)).isEqualTo(SURROGATE);
        assertThat(facilitatorOf(futureSession)).isNull();
        assertThat(presentersOf(slotOf(pastSession))).containsExactlyInAnyOrder(SURROGATE, NEIGHBOUR);
        assertThat(presentersOf(slotOf(futureSession))).containsExactly(NEIGHBOUR);
        assertThat(counts.deleted()).isEqualTo(1);
        assertThat(counts.cleared()).isEqualTo(1);
        assertThat(counts.anonymised()).isEqualTo(2);
    }

    @Test
    void anonymisesVotesAndAttendanceWithoutChangingTheirTotals() {

        var topicId = seedTopic(NEIGHBOUR);
        var sessionId = seedSession(NEIGHBOUR, Instant.parse("2026-01-01T10:00:00Z"));
        // a second, unrelated session so a scope-drop on the attendance update cannot hide behind a unique-constraint
        // collision the way the topic_vote statement does - only an assertion by value would catch it here
        var neighbourSession = seedSession(NEIGHBOUR, Instant.parse("2026-02-01T10:00:00Z"));
        seedVote(TARGET, topicId);
        seedVote(NEIGHBOUR, topicId);
        seedAttendance(TARGET, sessionId);
        seedAttendance(NEIGHBOUR, neighbourSession);

        var counts = eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET).surrogateId(SURROGATE)
                .erasedAt(ERASED_AT).build());

        assertThat(voteCount(topicId)).isEqualTo(2);
        assertThat(attendanceCount(sessionId)).isEqualTo(1);
        assertThat(voterOf(topicId, SURROGATE)).isEqualTo(1);
        // the neighbour is guarded by value here, not only by the unique constraint a too-broad update would trip
        assertThat(voterOf(topicId, NEIGHBOUR)).isEqualTo(1);
        assertThat(proposerOf(topicId)).isEqualTo(NEIGHBOUR);
        assertThat(facilitatorOf(sessionId)).isEqualTo(NEIGHBOUR);
        assertThat(attendeeOf(neighbourSession, NEIGHBOUR)).isEqualTo(1);
        assertThat(counts.deleted()).isZero();
        assertThat(counts.cleared()).isZero();
        assertThat(counts.anonymised()).isEqualTo(2);
    }

    /**
     * The {@code action_item} table's own assignee split: a closed item has nothing left to honour, so its assignment
     * becomes history like everything else, but an open one is still a live dispatch. {@code DROPPED} closes without
     * credit exactly like {@code DONE}.
     */
    @Test
    void clearsTheAssigneeOfAnOpenActionItemButAnonymisesADroppedOneInstead() {

        var openItem = seedNewActionItem(NEIGHBOUR, TARGET, "OPEN");
        var droppedItem = seedNewActionItem(NEIGHBOUR, TARGET, "DROPPED");
        var neighbourOpenItem = seedNewActionItem(NEIGHBOUR, NEIGHBOUR, "OPEN");

        var counts = eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET).surrogateId(SURROGATE)
                .erasedAt(ERASED_AT).build());

        // an open item is still a live dispatch to nobody now, so it is cleared rather than kept pointing at anybody
        assertThat(assigneeOfActionItem(openItem)).isNull();
        // a dropped item has nothing left to honour, so who was assigned is community history and is kept, unnamed
        assertThat(assigneeOfActionItem(droppedItem)).isEqualTo(SURROGATE);
        // guards a scope-drop on either statement: the neighbour's own assignment, untouched by TARGET's erasure
        assertThat(assigneeOfActionItem(neighbourOpenItem)).isEqualTo(NEIGHBOUR);
        assertThat(counts.cleared()).isEqualTo(1);
        assertThat(counts.anonymised()).isEqualTo(1);
        assertThat(counts.deleted()).isZero();
    }

    /**
     * Unlike the assignee, {@code action_item.raised_by_user_id} carries no outcome-based split: whoever raised an item
     * is community history the moment it exists, open or not.
     */
    @Test
    void anonymisesTheRaiserOfAnActionItemRegardlessOfItsOutcome() {

        var openItem = seedNewActionItem(TARGET, NEIGHBOUR, "OPEN");
        var doneItem = seedNewActionItem(TARGET, NEIGHBOUR, "DONE");
        var neighbourItem = seedNewActionItem(NEIGHBOUR, NEIGHBOUR, "OPEN");

        eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET).surrogateId(SURROGATE)
                .erasedAt(ERASED_AT).build());

        assertThat(raisedByOfActionItem(openItem)).isEqualTo(SURROGATE);
        assertThat(raisedByOfActionItem(doneItem)).isEqualTo(SURROGATE);
        // guards a scope-drop on "AND raised_by_user_id = :user": the neighbour's own item, untouched by TARGET's
        // erasure, is the thing a too-broad WHERE clause would wrongly anonymise
        assertThat(raisedByOfActionItem(neighbourItem)).isEqualTo(NEIGHBOUR);
    }

    /**
     * {@code action_item_checkpoint} has neither {@code @Version} nor {@code updated_at} to stamp, exactly like
     * {@code event_slot_presenter}: it is a plain log row, not a versioned entity.
     */
    @Test
    void anonymisesTheDeciderOfAnActionItemCheckpoint() {

        var itemId = seedNewActionItem(NEIGHBOUR, NEIGHBOUR, "DONE");
        var sessionId = seedSession(NEIGHBOUR, Instant.parse("2026-01-01T10:00:00Z"));
        var checkpointId = seedActionItemCheckpoint(itemId, sessionId, TARGET);
        var neighbourCheckpointId = seedActionItemCheckpoint(itemId, sessionId, NEIGHBOUR);

        eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET).surrogateId(SURROGATE)
                .erasedAt(ERASED_AT).build());

        assertThat(deciderOf(checkpointId)).isEqualTo(SURROGATE);
        assertThat(deciderOf(neighbourCheckpointId)).isEqualTo(NEIGHBOUR);
    }

    private Long seedNewActionItem(String raisedBy, String assignee, String outcome) {

        return seedNewActionItem(ORG, communityId, raisedBy, assignee, outcome);
    }

    private Long seedNewActionItem(String org, Long forCommunityId, String raisedBy, String assignee, String outcome) {

        return jdbc.queryForObject(
                "INSERT INTO action_item (organization_id, community_id, title, description, raised_by_user_id, "
                        + "assignee_user_id, outcome, created_at, updated_at, version) VALUES (:org, :communityId, "
                        + "'Eraser Test Action Item', '', :raisedBy, :assignee, :outcome, now(), now(), 0) "
                        + "RETURNING id",
                Map.of("org", org, "communityId", forCommunityId, "raisedBy", raisedBy, "assignee", assignee, "outcome",
                        outcome),
                Long.class);
    }

    private Long seedActionItemCheckpoint(Long actionItemId, Long sessionId, String decidedBy) {

        return jdbc.queryForObject(
                "INSERT INTO action_item_checkpoint (organization_id, action_item_id, session_id, outcome, "
                        + "decided_by_user_id, decided_at) VALUES (:org, :actionItemId, :sessionId, 'CARRIED', "
                        + ":decidedBy, now()) RETURNING id",
                Map.of("org", ORG, "actionItemId", actionItemId, "sessionId", sessionId, "decidedBy", decidedBy),
                Long.class);
    }

    private String assigneeOfActionItem(Long actionItemId) {

        return jdbc.queryForObject("SELECT assignee_user_id FROM action_item WHERE id = :id",
                Map.of("id", actionItemId), String.class);
    }

    private String raisedByOfActionItem(Long actionItemId) {

        return jdbc.queryForObject("SELECT raised_by_user_id FROM action_item WHERE id = :id",
                Map.of("id", actionItemId), String.class);
    }

    private String deciderOf(Long checkpointId) {

        return jdbc.queryForObject("SELECT decided_by_user_id FROM action_item_checkpoint WHERE id = :id",
                Map.of("id", checkpointId), String.class);
    }

    /**
     * The subscription is a live credential, not history, so the member's own row is deleted outright rather than
     * anonymised - and, because it is a credential, an erasure that missed it would leave the person's feed URL still
     * resolving. A colleague's subscription in the same community and the member's own subscription in another
     * organization are asserted by value, the same way every other statement in this class is: only a by-value
     * assertion on a row that survives either way notices a dropped {@code organization_id} or {@code user_id}
     * predicate.
     */
    @Test
    void deletesTheMembersCalendarSubscriptionButNeitherAColleaguesNorAnotherOrganizations() {

        var otherOrgCommunityId = seedCommunity(OTHER_ORG);
        seedSubscription(ORG, communityId, TARGET, "target-subscription-hash");
        seedSubscription(ORG, communityId, NEIGHBOUR, "neighbour-subscription-hash");
        seedSubscription(OTHER_ORG, otherOrgCommunityId, TARGET, "other-org-subscription-hash");

        var counts = eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET).surrogateId(SURROGATE)
                .erasedAt(ERASED_AT).build());

        assertThat(subscriptionExists(ORG, TARGET)).isFalse();
        assertThat(subscriptionExists(ORG, NEIGHBOUR)).isTrue();
        assertThat(subscriptionExists(OTHER_ORG, TARGET)).isTrue();
        assertThat(counts.deleted()).isEqualTo(1);
    }

    @Test
    void scopesEveryStatementByTheOrganizationBeingErasedNotJustTheUserId() {

        var otherOrgCommunityId = seedCommunity(OTHER_ORG);
        var otherOrgSectionTypeId = seedSectionType(OTHER_ORG);
        var otherOrgPastSession = seedSessionIn(OTHER_ORG, otherOrgCommunityId, TARGET,
                Instant.parse("2026-01-01T10:00:00Z"));
        var otherOrgFutureSession = seedSessionIn(OTHER_ORG, otherOrgCommunityId, TARGET,
                Instant.parse("2026-12-01T10:00:00Z"));
        var otherOrgPastSlot = createSlotIn(otherOrgPastSession, otherOrgSectionTypeId);
        var otherOrgFutureSlot = createSlotIn(otherOrgFutureSession, otherOrgSectionTypeId);
        seedPresenter(TARGET, otherOrgPastSlot);
        seedPresenter(TARGET, otherOrgFutureSlot);
        var otherOrgTopic = seedTopicIn(OTHER_ORG, otherOrgCommunityId, TARGET);
        var otherOrgOpenItem = seedNewActionItem(OTHER_ORG, otherOrgCommunityId, TARGET, TARGET, "OPEN");
        var otherOrgDoneItem = seedNewActionItem(OTHER_ORG, otherOrgCommunityId, TARGET, TARGET, "DONE");
        seedVote(OTHER_ORG, TARGET, otherOrgTopic);
        seedAttendance(OTHER_ORG, TARGET, otherOrgPastSession);

        eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET).surrogateId(SURROGATE)
                .erasedAt(ERASED_AT).build());

        // every one of the eraser's statements is scoped by "organization_id = :org" (or, for event_slot_presenter,
        // by the session it joins to) - none of these other-org rows, all naming TARGET, may be touched by an
        // erasure of "default", regardless of whether TARGET's role in them is past, future, open or done
        assertThat(facilitatorOf(otherOrgPastSession)).isEqualTo(TARGET);
        assertThat(facilitatorOf(otherOrgFutureSession)).isEqualTo(TARGET);
        assertThat(presentersOf(otherOrgPastSlot)).containsExactly(TARGET);
        assertThat(presentersOf(otherOrgFutureSlot)).containsExactly(TARGET);
        assertThat(proposerOf(otherOrgTopic)).isEqualTo(TARGET);
        assertThat(voterOf(otherOrgTopic, TARGET)).isEqualTo(1);
        assertThat(attendeeOf(otherOrgPastSession, TARGET)).isEqualTo(1);
        assertThat(assigneeOfActionItem(otherOrgOpenItem)).isEqualTo(TARGET);
        assertThat(assigneeOfActionItem(otherOrgDoneItem)).isEqualTo(TARGET);
        assertThat(raisedByOfActionItem(otherOrgOpenItem)).isEqualTo(TARGET);
    }

    private Long seedCommunity(String org) {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES (:org, 'Eraser Test Guild', now(), now(), 0)", Map.of("org", org));
        return jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, "
                        + "updated_at, version) SELECT :org, 'eraser-test-community', 'Eraser Test Community', k.id, "
                        + "'', 'ACTIVE', now(), now(), 0 FROM community_kind k WHERE k.organization_id = :org "
                        + "AND k.label = 'Eraser Test Guild' RETURNING id",
                Map.of("org", org), Long.class);
    }

    private Long seedSectionType(String org) {

        return jdbc.queryForObject(
                "INSERT INTO event_section_type (organization_id, label, expects_presenter, created_at, updated_at, "
                        + "version) VALUES (:org, 'Eraser Test Section Type', true, now(), now(), 0) RETURNING id",
                Map.of("org", org), Long.class);
    }

    private Long seedSessionIn(String org, Long forCommunityId, String facilitator, Instant scheduledAt) {

        return jdbc.queryForObject(
                "INSERT INTO community_session (organization_id, community_id, status, scheduled_at, "
                        + "facilitator_user_id, notes, recording_url, has_time, created_at, updated_at, version) VALUES "
                        + "(:org, :communityId, 'SCHEDULED', :scheduledAt, :facilitator, '', '', true, now(), now(), 0) "
                        + "RETURNING id",
                Map.of("org", org, "communityId", forCommunityId, "scheduledAt", Timestamp.from(scheduledAt),
                        "facilitator", facilitator),
                Long.class);
    }

    private Long seedSession(String facilitator, Instant scheduledAt) {

        return seedSessionIn(ORG, communityId, facilitator, scheduledAt);
    }

    private void seedSubscription(String org, Long forCommunityId, String user, String tokenHash) {

        jdbc.update(
                "INSERT INTO calendar_subscription (organization_id, community_id, user_id, token_hash, "
                        + "created_at, version) VALUES (:org, :communityId, :user, :tokenHash, now(), 0)",
                Map.of("org", org, "communityId", forCommunityId, "user", user, "tokenHash", tokenHash));
    }

    private boolean subscriptionExists(String org, String user) {

        return jdbc.queryForObject(
                "SELECT count(*) FROM calendar_subscription WHERE organization_id = :org AND user_id = :user",
                Map.of("org", org, "user", user), Long.class) > 0;
    }

    private void seedPresenter(String user, Long slotId) {

        jdbc.update("INSERT INTO event_slot_presenter (slot_id, user_id) VALUES (:slotId, :user)",
                Map.of("slotId", slotId, "user", user));
    }

    private Long slotOf(Long sessionId) {

        return slotBySession.computeIfAbsent(sessionId, this::createSlotFor);
    }

    private Long createSlotFor(Long sessionId) {

        return createSlotIn(sessionId, sectionTypeId);
    }

    private Long createSlotIn(Long sessionId, Long forSectionTypeId) {

        var sectionId = jdbc.queryForObject(
                "INSERT INTO event_section (session_id, \"position\", section_type_id, title) VALUES "
                        + "(:sessionId, 0, :sectionTypeId, 'Eraser Test Section') RETURNING id",
                Map.of("sessionId", sessionId, "sectionTypeId", forSectionTypeId), Long.class);
        return jdbc.queryForObject(
                "INSERT INTO event_slot (section_id, \"position\", duration_minutes, title) VALUES "
                        + "(:sectionId, 0, 10, 'Eraser Test Slot') RETURNING id",
                Map.of("sectionId", sectionId), Long.class);
    }

    private Long seedTopic(String proposer) {

        return seedTopicIn(ORG, communityId, proposer);
    }

    private Long seedTopicIn(String org, Long forCommunityId, String proposer) {

        return jdbc.queryForObject(
                "INSERT INTO topic (organization_id, community_id, kind, title, description, proposer_user_id, "
                        + "status, created_at, updated_at, version) VALUES (:org, :communityId, 'REQUEST', "
                        + "'Eraser Test Topic', '', :proposer, 'PROPOSED', now(), now(), 0) RETURNING id",
                Map.of("org", org, "communityId", forCommunityId, "proposer", proposer), Long.class);
    }

    private void seedVote(String org, String user, Long topicId) {

        jdbc.update(
                "INSERT INTO topic_vote (organization_id, topic_id, user_id, created_at, updated_at, version) "
                        + "VALUES (:org, :topicId, :user, now(), now(), 0)",
                Map.of("org", org, "topicId", topicId, "user", user));
    }

    private void seedVote(String user, Long topicId) {

        seedVote(ORG, user, topicId);
    }

    private void seedAttendance(String org, String user, Long sessionId) {

        jdbc.update(
                "INSERT INTO session_attendance (organization_id, session_id, user_id, created_at, updated_at, "
                        + "version) VALUES (:org, :sessionId, :user, now(), now(), 0)",
                Map.of("org", org, "sessionId", sessionId, "user", user));
    }

    private void seedAttendance(String user, Long sessionId) {

        seedAttendance(ORG, user, sessionId);
    }

    private String facilitatorOf(Long sessionId) {

        return jdbc.queryForObject("SELECT facilitator_user_id FROM community_session WHERE id = :id",
                Map.of("id", sessionId), String.class);
    }

    private List<String> presentersOf(Long slotId) {

        return jdbc.queryForList("SELECT user_id FROM event_slot_presenter WHERE slot_id = :slotId",
                Map.of("slotId", slotId), String.class);
    }

    private String proposerOf(Long topicId) {

        return jdbc.queryForObject("SELECT proposer_user_id FROM topic WHERE id = :id", Map.of("id", topicId),
                String.class);
    }

    private long voteCount(Long topicId) {

        return jdbc.queryForObject("SELECT count(*) FROM topic_vote WHERE topic_id = :id", Map.of("id", topicId),
                Long.class);
    }

    private long attendanceCount(Long sessionId) {

        return jdbc.queryForObject("SELECT count(*) FROM session_attendance WHERE session_id = :id",
                Map.of("id", sessionId), Long.class);
    }

    private long attendeeOf(Long sessionId, String user) {

        return jdbc.queryForObject("SELECT count(*) FROM session_attendance WHERE session_id = :id AND user_id = :user",
                Map.of("id", sessionId, "user", user), Long.class);
    }

    private long voterOf(Long topicId, String user) {

        return jdbc.queryForObject("SELECT count(*) FROM topic_vote WHERE topic_id = :id AND user_id = :user",
                Map.of("id", topicId, "user", user), Long.class);
    }
}
