package dev.axonia.events.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.events.api.ActionItemOutcome;
import dev.axonia.events.application.ActionItemFilter;
import dev.axonia.events.domain.CheckpointOutcome;

import static org.assertj.core.api.Assertions.assertThat;

class ActionItemRepositoryTest extends CoreJdbcTestSupport {

    @Autowired
    private ActionItemRepository actionItemRepository;

    @Autowired
    private ActionItemCheckpointRepository actionItemCheckpointRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SeriesRepository seriesRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seedCommunity() {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES ('default', 'Guild', now(), now(), 0) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO communities (id, organization_id, slug, name, kind_id, description, status, "
                + "created_at, updated_at, version) SELECT 1,'default','c1','C1', k.id,'','ACTIVE', now(), now(), 0 "
                + "FROM community_kind k WHERE k.organization_id='default' AND k.label='Guild' ON CONFLICT DO NOTHING");
    }

    @Test
    void findFiltered_withNoOutcomeReturnsEveryOutcome() {

        actionItemRepository.save(anyItem().outcome(ActionItemOutcome.OPEN).build());
        actionItemRepository.save(anyItem().outcome(ActionItemOutcome.DONE).build());

        var page = actionItemRepository.findFiltered("default", 1L, noFilter(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findFiltered_byOutcomeNarrowsTheResult() {

        actionItemRepository.save(anyItem().outcome(ActionItemOutcome.OPEN).build());
        var done = actionItemRepository.save(anyItem().outcome(ActionItemOutcome.DONE).build());

        var page = actionItemRepository.findFiltered("default", 1L, outcomeFilter(ActionItemOutcome.DONE),
                PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(ActionItemEntity::getId).containsExactly(done.getId());
    }

    @Test
    void findFiltered_isScopedToTheCommunity() {

        jdbc.update("INSERT INTO communities (id, organization_id, slug, name, kind_id, description, status, "
                + "created_at, updated_at, version) SELECT 2,'default','c2','C2', k.id,'','ACTIVE', now(), now(), 0 "
                + "FROM community_kind k WHERE k.organization_id='default' AND k.label='Guild' ON CONFLICT DO NOTHING");
        actionItemRepository.save(anyItem().communityId(1L).build());
        actionItemRepository.save(anyItem().communityId(2L).build());

        var page = actionItemRepository.findFiltered("default", 1L, noFilter(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    /**
     * Testcontainers connects as the schema owner, which bypasses RLS by ownership - the {@code organization_id}
     * predicate in {@code ActionItemRepositoryCustomImpl}'s own {@code WHERE} clause is the only real cross-tenant
     * defence this list route has, and {@code findFiltered_isScopedToTheCommunity} above only proves community
     * isolation within one organization, never across two. {@code findFiltered} filters on
     * {@code action_item.organization_id} directly, never through a join to {@code communities.organization_id}, so the
     * second item only needs an {@code organization_id} of its own - it is deliberately pointed at the same
     * {@code community_id} (1) that already exists under {@code 'default'}; standing up a second, genuinely
     * {@code 'other-org'}-owned community would prove nothing this query even looks at.
     */
    @Test
    void findFiltered_isScopedToTheOrganization() {

        actionItemRepository.save(anyItem().build());
        actionItemRepository.save(ActionItemEntity.builder().organizationId("other-org").communityId(1L)
                .title("Follow up").description("").raisedByUserId("alice").outcome(ActionItemOutcome.OPEN).build());

        var page = actionItemRepository.findFiltered("default", 1L, noFilter(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void findFiltered_pageBeyondTheLastRow_returnsEmptyContentWithTheRealTotal() {

        actionItemRepository.save(anyItem().build());

        var page = actionItemRepository.findFiltered("default", 1L, noFilter(), PageRequest.of(1, 10));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    /**
     * The action-items tab groups by checkpoint session, ordered by checkpoint date then due date. A pinned item's
     * checkpoint date is its own session's {@code scheduled_at}; an unpinned item's is the community's own soonest
     * upcoming session - here, earlier than the pinned one, so an unpinned item with a later due date still sorts
     * first. Session times and due dates are offsets from a captured {@code now} rather than literal calendar dates, so
     * this passes on any day the gate happens to run - {@code ActionItemRepositoryCustomImpl} binds {@code :now} from
     * the same injected {@link java.time.Clock} production reads, so there is nothing left in this query for a real
     * clock to drift against.
     */
    @Test
    void findFiltered_checkpointSortOrdersByCheckpointDateThenDueDate() {

        var now = Instant.now();
        var seriesId = seriesRepository.save(SeriesEntityMother.any().communityId(1L).build()).getId();
        var soonSessionId = sessionRepository
                .save(SessionEntityMother.any().seriesId(seriesId).communityId(1L)
                        .scheduledAt(now.plusSeconds(5 * 86_400L))
                        .occurrenceDate(now.plusSeconds(5 * 86_400L).atOffset(ZoneOffset.UTC).toLocalDate()).build())
                .getId();
        var laterSessionId = sessionRepository
                .save(SessionEntityMother.any().seriesId(seriesId).communityId(1L)
                        .scheduledAt(now.plusSeconds(50 * 86_400L))
                        .occurrenceDate(now.plusSeconds(50 * 86_400L).atOffset(ZoneOffset.UTC).toLocalDate()).build())
                .getId();

        var today = LocalDate.now(ZoneOffset.UTC);
        var pinnedLate = actionItemRepository
                .save(anyItem().title("Pinned late").checkpointSessionId(laterSessionId).dueDate(today).build());
        var unpinnedEarlyDue = actionItemRepository
                .save(anyItem().title("Unpinned, due sooner").dueDate(today.plusDays(1)).build());
        var unpinnedLateDue = actionItemRepository
                .save(anyItem().title("Unpinned, due later").dueDate(today.plusDays(7)).build());

        var page = actionItemRepository.findFiltered("default", 1L, noFilter(),
                PageRequest.of(0, 20, Sort.by("checkpoint")));

        assertThat(page.getContent()).extracting(ActionItemEntity::getId).containsExactly(unpinnedEarlyDue.getId(),
                unpinnedLateDue.getId(), pinnedLate.getId());
    }

    /**
     * The Past tab truncates on the server and displays what comes back, so the settle order has to be the server's
     * own. The three rows are written in one creation order and stamped with a different {@code updated_at} order, so a
     * clause still ordering by {@code created_at} cannot pass this by accident.
     */
    @Test
    void findFiltered_updatedAtSortOrdersBySettleTimeNotCreationTime() {

        var settledFirst = actionItemRepository.save(anyItem().title("Settled yesterday").build());
        var settledOldest = actionItemRepository.save(anyItem().title("Settled long ago").build());
        var settledMiddle = actionItemRepository.save(anyItem().title("Settled last week").build());
        var now = Instant.now();
        stampUpdatedAt(settledFirst.getId(), now.minusSeconds(86_400L));
        stampUpdatedAt(settledOldest.getId(), now.minusSeconds(10 * 86_400L));
        stampUpdatedAt(settledMiddle.getId(), now.minusSeconds(5 * 86_400L));

        var page = actionItemRepository.findFiltered("default", 1L, noFilter(),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updated_at")));

        assertThat(page.getContent()).extracting(ActionItemEntity::getId).containsExactly(settledFirst.getId(),
                settledMiddle.getId(), settledOldest.getId());
    }

    /**
     * Written newest-settled first so that {@code created_at DESC}, the clause a dropped {@code updated_at} branch
     * falls through to, cannot produce this order by accident.
     */
    @Test
    void findFiltered_updatedAtSortHonoursAnAscendingRequest() {

        var older = actionItemRepository.save(anyItem().title("Older").build());
        var newer = actionItemRepository.save(anyItem().title("Newer").build());
        var now = Instant.now();
        stampUpdatedAt(newer.getId(), now.minusSeconds(86_400L));
        stampUpdatedAt(older.getId(), now.minusSeconds(10 * 86_400L));

        var page = actionItemRepository.findFiltered("default", 1L, noFilter(),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "updated_at")));

        assertThat(page.getContent()).extracting(ActionItemEntity::getId).containsExactly(older.getId(), newer.getId());
    }

    // --- findFiltered: origin and checkpoint session filters -----------

    /**
     * The checkpoint filter reads the resolved checkpoint, never the stored column alone: an item pinned to the session
     * matches, and so does an unpinned one that late-binds to it as the community's soonest still-upcoming session.
     */
    @Test
    void findFiltered_byCheckpointSession_matchesPinnedAndLateBoundItems() {

        var now = Instant.now();
        var soon = sessionAt(1L, now.plusSeconds(5 * 86_400L));
        var later = sessionAt(1L, now.plusSeconds(50 * 86_400L));
        var pinnedHere = actionItemRepository.save(anyItem().title("Pinned here").checkpointSessionId(soon).build());
        var lateBound = actionItemRepository.save(anyItem().title("Unpinned").build());
        actionItemRepository.save(anyItem().title("Pinned elsewhere").checkpointSessionId(later).build());

        var page = actionItemRepository.findFiltered("default", 1L, checkpointFilter(soon), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(ActionItemEntity::getId).containsExactlyInAnyOrder(pinnedHere.getId(),
                lateBound.getId());
    }

    /**
     * A settled item is not waiting on anything, so it never late-binds - even to the session that would otherwise be
     * the community's soonest upcoming one.
     */
    @Test
    void findFiltered_byCheckpointSession_excludesASettledItemThatWouldLateBindToIt() {

        var now = Instant.now();
        var upcoming = sessionAt(1L, now.plusSeconds(5 * 86_400L));
        actionItemRepository.save(anyItem().title("Answered long ago").outcome(ActionItemOutcome.DONE).build());

        var page = actionItemRepository.findFiltered("default", 1L, checkpointFilter(upcoming), PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
    }

    /**
     * A settled item's own pin still names a session: only the late-binding fallback is closed off by settling.
     */
    @Test
    void findFiltered_byCheckpointSession_stillMatchesASettledItemPinnedToIt() {

        var now = Instant.now();
        var session = sessionAt(1L, now.plusSeconds(5 * 86_400L));
        var pinned = actionItemRepository.save(
                anyItem().title("Answered here").outcome(ActionItemOutcome.DONE).checkpointSessionId(session).build());

        var page = actionItemRepository.findFiltered("default", 1L, checkpointFilter(session), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(ActionItemEntity::getId).containsExactly(pinned.getId());
    }

    /**
     * An unpinned item late-binds to the community's soonest upcoming session, not to whichever upcoming session is
     * being asked about: filtering on the later of two upcoming sessions returns only what is pinned to it.
     */
    @Test
    void findFiltered_byCheckpointSession_excludesAnItemLateBindingToAnEarlierSession() {

        var now = Instant.now();
        sessionAt(1L, now.plusSeconds(5 * 86_400L));
        var later = sessionAt(1L, now.plusSeconds(50 * 86_400L));
        actionItemRepository.save(anyItem().title("Unpinned").build());
        var pinnedLater = actionItemRepository.save(anyItem().title("Pinned later").checkpointSessionId(later).build());

        var page = actionItemRepository.findFiltered("default", 1L, checkpointFilter(later), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(ActionItemEntity::getId).containsExactly(pinnedLater.getId());
    }

    /**
     * A session id is visible org-wide, so a matching checkpoint session proves nothing about whose item it is - the
     * caller's community and tenant still decide, exactly as they do with no filter at all.
     */
    @Test
    void findFiltered_byCheckpointSession_excludesOtherCommunitiesAndOtherOrganizations() {

        seedCommunity(2L, "c2");
        var session = sessionAt(1L, Instant.now().plusSeconds(5 * 86_400L));
        var mine = actionItemRepository.save(anyItem().title("Mine").checkpointSessionId(session).build());
        actionItemRepository
                .save(anyItem().title("Other community").communityId(2L).checkpointSessionId(session).build());
        actionItemRepository
                .save(anyItem().title("Other org").organizationId("other-org").checkpointSessionId(session).build());

        var page = actionItemRepository.findFiltered("default", 1L, checkpointFilter(session), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(ActionItemEntity::getId).containsExactly(mine.getId());
    }

    /**
     * A pin naming another community's session can only exist if a write bypassed {@code SessionCommunityGuard}. The
     * read does not rest on that guard: the pinned branch carries its own tenant and community predicates, so such a
     * row resolves as unpinned rather than as that foreign session.
     */
    @Test
    void findFiltered_byCheckpointSession_ignoresAPinPointingIntoAnotherCommunity() {

        seedCommunity(2L, "c2");
        var foreign = sessionAt(2L, Instant.now().plusSeconds(5 * 86_400L));
        actionItemRepository.save(anyItem().title("Pinned across the fence").checkpointSessionId(foreign).build());

        var page = actionItemRepository.findFiltered("default", 1L, checkpointFilter(foreign), PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void findFiltered_byOriginSession_returnsOnlyTheItemsRaisedAtThatSession() {

        seedSession(42L);
        seedSession(43L);
        var raisedHere = actionItemRepository.save(anyItem().title("Raised here").originSessionId(42L).build());
        actionItemRepository.save(anyItem().title("Raised elsewhere").originSessionId(43L).build());
        actionItemRepository.save(anyItem().title("Raised nowhere").build());

        var page = actionItemRepository.findFiltered("default", 1L, new ActionItemFilter(null, 42L, null),
                PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(ActionItemEntity::getId).containsExactly(raisedHere.getId());
    }

    @Test
    void findFiltered_combinesOutcomeOriginAndCheckpointFilters() {

        seedSession(42L);
        seedSession(43L);
        var now = Instant.now();
        var soon = sessionAt(1L, now.plusSeconds(5 * 86_400L));
        var later = sessionAt(1L, now.plusSeconds(50 * 86_400L));
        var wanted = actionItemRepository.save(anyItem().title("Wanted").originSessionId(42L).checkpointSessionId(soon)
                .outcome(ActionItemOutcome.OPEN).build());
        actionItemRepository.save(anyItem().title("Wrong outcome").originSessionId(42L).checkpointSessionId(soon)
                .outcome(ActionItemOutcome.DONE).build());
        actionItemRepository.save(anyItem().title("Wrong origin").originSessionId(43L).checkpointSessionId(soon)
                .outcome(ActionItemOutcome.OPEN).build());
        actionItemRepository.save(anyItem().title("Wrong checkpoint").originSessionId(42L).checkpointSessionId(later)
                .outcome(ActionItemOutcome.OPEN).build());

        var page = actionItemRepository.findFiltered("default", 1L,
                new ActionItemFilter(ActionItemOutcome.OPEN, 42L, soon), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(ActionItemEntity::getId).containsExactly(wanted.getId());
    }

    /**
     * The count and the id query share one where clause. A filter reaching only one of them gives a page whose
     * {@code totalElements} contradicts its own content. The page size is 1, not the usual 20: on a page that does not
     * fill, {@code PageImpl} rewrites the total it is handed to {@code offset + content.size()}, which hides exactly
     * the disagreement this asserts.
     */
    @Test
    void findFiltered_byCheckpointSession_totalAgreesWithTheReturnedPage() {

        var now = Instant.now();
        var soon = sessionAt(1L, now.plusSeconds(5 * 86_400L));
        var later = sessionAt(1L, now.plusSeconds(50 * 86_400L));
        actionItemRepository.save(anyItem().title("Answered here").checkpointSessionId(soon).build());
        actionItemRepository.save(anyItem().title("Answered later").checkpointSessionId(later).build());
        actionItemRepository.save(anyItem().title("Also later").checkpointSessionId(later).build());

        var page = actionItemRepository.findFiltered("default", 1L, checkpointFilter(soon), PageRequest.of(0, 1));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void findByOrganizationIdAndActionItemIdInAndOutcome_batchesTheCarriedRowsOfEveryItemOnThePage() {

        var seriesId = seriesRepository.save(SeriesEntityMother.any().communityId(1L).build()).getId();
        var sessionId = sessionRepository.save(SessionEntityMother.any().seriesId(seriesId).communityId(1L)
                .scheduledAt(Instant.parse("2026-09-20T09:00:00Z")).build()).getId();
        var carriedTwice = actionItemRepository.save(anyItem().build());
        var carriedOnce = actionItemRepository.save(anyItem().build());
        var neverCarried = actionItemRepository.save(anyItem().build());
        checkpointRow(carriedTwice.getId(), sessionId, CheckpointOutcome.CARRIED);
        checkpointRow(carriedTwice.getId(), sessionId, CheckpointOutcome.CARRIED);
        checkpointRow(carriedOnce.getId(), sessionId, CheckpointOutcome.CARRIED);
        checkpointRow(neverCarried.getId(), sessionId, CheckpointOutcome.DONE);

        var rows = actionItemCheckpointRepository.findByOrganizationIdAndActionItemIdInAndOutcome("default",
                List.of(carriedTwice.getId(), carriedOnce.getId(), neverCarried.getId()), CheckpointOutcome.CARRIED);

        assertThat(rows).extracting(ActionItemCheckpointEntity::getActionItemId)
                .containsExactlyInAnyOrder(carriedTwice.getId(), carriedTwice.getId(), carriedOnce.getId());
    }

    // --- findByOriginSessionId -----------------------------------------

    @Test
    void findByOriginSessionId_ordersOpenItemsByDueDateThenSinksResolvedOnesLast() {

        seedSession(42L);
        var laterDue = actionItemRepository.save(anyItem().title("Later due").originSessionId(42L)
                .dueDate(LocalDate.of(2026, 9, 1)).outcome(ActionItemOutcome.OPEN).build());
        var noDue = actionItemRepository.save(anyItem().title("No due date").originSessionId(42L).dueDate(null)
                .outcome(ActionItemOutcome.OPEN).build());
        var earlierDue = actionItemRepository.save(anyItem().title("Earlier due").originSessionId(42L)
                .dueDate(LocalDate.of(2026, 8, 1)).outcome(ActionItemOutcome.OPEN).build());
        var done = actionItemRepository.save(anyItem().title("Done, earliest due").originSessionId(42L)
                .dueDate(LocalDate.of(2026, 1, 1)).outcome(ActionItemOutcome.DONE).build());

        var results = actionItemRepository.findByOriginSessionId("default", 1L, 42L);

        assertThat(results).extracting(ActionItemEntity::getId).containsExactly(earlierDue.getId(), laterDue.getId(),
                noDue.getId(), done.getId());
    }

    /**
     * A row planted before this filter existed: an item whose own community differs from the session it names as its
     * origin. The session id itself is org-visible, so origin_session_id matching alone proves nothing about which
     * community the session belongs to - the item's own community_id must agree with the caller's.
     */
    @Test
    void findByOriginSessionId_excludesAnItemFromAnotherCommunityEvenIfItsOriginSessionIdMatches() {

        seedSession(42L);
        jdbc.update("INSERT INTO communities (id, organization_id, slug, name, kind_id, description, status, "
                + "created_at, updated_at, version) SELECT 999,'default','c999','C999', k.id,'','ACTIVE', now(), now(), 0 "
                + "FROM community_kind k WHERE k.organization_id='default' AND k.label='Guild' ON CONFLICT DO NOTHING");
        actionItemRepository.save(anyItem().communityId(999L).originSessionId(42L).build());

        var results = actionItemRepository.findByOriginSessionId("default", 1L, 42L);

        assertThat(results).isEmpty();
    }

    // --- findByOrganizationIdAndCommunityIdAndSessionId (checkpoint finder) --

    @Test
    void findByOrganizationIdAndCommunityIdAndSessionId_returnsOnlyThisSessionsRows() {

        var seriesId = seriesRepository.save(SeriesEntityMother.any().communityId(1L).build()).getId();
        var thisSessionId = sessionRepository.save(SessionEntityMother.any().seriesId(seriesId).communityId(1L)
                .scheduledAt(Instant.parse("2026-09-20T09:00:00Z")).occurrenceDate(LocalDate.of(2026, 9, 20)).build())
                .getId();
        var otherSessionId = sessionRepository.save(SessionEntityMother.any().seriesId(seriesId).communityId(1L)
                .scheduledAt(Instant.parse("2026-09-21T09:00:00Z")).occurrenceDate(LocalDate.of(2026, 9, 21)).build())
                .getId();
        var item = actionItemRepository.save(anyItem().build());
        checkpointRow(item.getId(), thisSessionId, CheckpointOutcome.CARRIED);
        checkpointRow(item.getId(), otherSessionId, CheckpointOutcome.DONE);

        var rows = actionItemCheckpointRepository.findByOrganizationIdAndCommunityIdAndSessionId("default", 1L,
                thisSessionId);

        assertThat(rows).extracting(ActionItemCheckpointEntity::getSessionId).containsExactly(thisSessionId);
    }

    /**
     * Mirrors {@code findFiltered_isScopedToTheOrganization}: the schema owner Testcontainers connects as bypasses RLS
     * by ownership, so the {@code organization_id} predicate in the query itself is the only real cross-tenant defence
     * this finder has.
     */
    @Test
    void findByOrganizationIdAndCommunityIdAndSessionId_isScopedToTheOrganization() {

        var seriesId = seriesRepository.save(SeriesEntityMother.any().communityId(1L).build()).getId();
        var sessionId = sessionRepository.save(SessionEntityMother.any().seriesId(seriesId).communityId(1L)
                .scheduledAt(Instant.parse("2026-09-20T09:00:00Z")).build()).getId();
        var item = actionItemRepository.save(anyItem().build());
        checkpointRow(item.getId(), sessionId, CheckpointOutcome.CARRIED);
        actionItemCheckpointRepository.save(ActionItemCheckpointEntity.builder().organizationId("other-org")
                .actionItemId(item.getId()).sessionId(sessionId).outcome(CheckpointOutcome.CARRIED)
                .decidedByUserId("alice").decidedAt(Instant.now()).build());

        var rows = actionItemCheckpointRepository.findByOrganizationIdAndCommunityIdAndSessionId("default", 1L,
                sessionId);

        assertThat(rows).hasSize(1);
    }

    /**
     * A checkpoint row's own {@code session_id} is never caller-supplied, but this finder checks the session's
     * community explicitly rather than trusting that invariant alone - the same defensive posture
     * {@code findByOriginSessionId} already takes. A session that belongs to another community must never leak through
     * just because its id happens to match.
     */
    @Test
    void findByOrganizationIdAndCommunityIdAndSessionId_excludesASessionFromAnotherCommunity() {

        jdbc.update("INSERT INTO communities (id, organization_id, slug, name, kind_id, description, status, "
                + "created_at, updated_at, version) SELECT 999,'default','c999','C999', k.id,'','ACTIVE', now(), now(), 0 "
                + "FROM community_kind k WHERE k.organization_id='default' AND k.label='Guild' ON CONFLICT DO NOTHING");
        var seriesId = seriesRepository.save(SeriesEntityMother.any().communityId(999L).build()).getId();
        var otherCommunitySessionId = sessionRepository.save(SessionEntityMother.any().seriesId(seriesId)
                .communityId(999L).scheduledAt(Instant.parse("2026-09-20T09:00:00Z")).build()).getId();
        var item = actionItemRepository.save(anyItem().communityId(999L).build());
        checkpointRow(item.getId(), otherCommunitySessionId, CheckpointOutcome.CARRIED);

        var rows = actionItemCheckpointRepository.findByOrganizationIdAndCommunityIdAndSessionId("default", 1L,
                otherCommunitySessionId);

        assertThat(rows).isEmpty();
    }

    @Test
    void findByOrganizationIdAndCommunityIdAndSessionId_ordersByDecidedAtThenId() {

        var seriesId = seriesRepository.save(SeriesEntityMother.any().communityId(1L).build()).getId();
        var sessionId = sessionRepository.save(SessionEntityMother.any().seriesId(seriesId).communityId(1L)
                .scheduledAt(Instant.parse("2026-09-20T09:00:00Z")).build()).getId();
        var item = actionItemRepository.save(anyItem().build());
        var later = actionItemCheckpointRepository.save(ActionItemCheckpointEntity.builder().organizationId("default")
                .actionItemId(item.getId()).sessionId(sessionId).outcome(CheckpointOutcome.CARRIED)
                .decidedByUserId("alice").decidedAt(Instant.parse("2026-09-20T10:00:00Z")).build());
        var earlier = actionItemCheckpointRepository.save(ActionItemCheckpointEntity.builder().organizationId("default")
                .actionItemId(item.getId()).sessionId(sessionId).outcome(CheckpointOutcome.DONE)
                .decidedByUserId("alice").decidedAt(Instant.parse("2026-09-20T09:00:00Z")).build());

        var rows = actionItemCheckpointRepository.findByOrganizationIdAndCommunityIdAndSessionId("default", 1L,
                sessionId);

        assertThat(rows).extracting(ActionItemCheckpointEntity::getId).containsExactly(earlier.getId(), later.getId());
    }

    private void stampUpdatedAt(Long actionItemId, Instant updatedAt) {

        jdbc.update("UPDATE action_item SET updated_at = ? WHERE id = ?", Timestamp.from(updatedAt), actionItemId);
    }

    private ActionItemFilter noFilter() {

        return new ActionItemFilter(null, null, null);
    }

    private ActionItemFilter outcomeFilter(ActionItemOutcome outcome) {

        return new ActionItemFilter(outcome, null, null);
    }

    private ActionItemFilter checkpointFilter(Long checkpointSessionId) {

        return new ActionItemFilter(null, null, checkpointSessionId);
    }

    private Long sessionAt(long communityId, Instant scheduledAt) {

        var seriesId = seriesRepository.save(SeriesEntityMother.any().communityId(communityId).build()).getId();
        return sessionRepository.save(SessionEntityMother.any().seriesId(seriesId).communityId(communityId)
                .scheduledAt(scheduledAt).occurrenceDate(scheduledAt.atOffset(ZoneOffset.UTC).toLocalDate()).build())
                .getId();
    }

    private void seedCommunity(long id, String slug) {

        jdbc.update("INSERT INTO communities (id, organization_id, slug, name, kind_id, description, status, "
                + "created_at, updated_at, version) SELECT ?,'default',?,?, k.id,'','ACTIVE', now(), now(), 0 "
                + "FROM community_kind k WHERE k.organization_id='default' AND k.label='Guild' ON CONFLICT DO NOTHING",
                id, slug, slug.toUpperCase(java.util.Locale.ROOT));
    }

    private void seedSession(long sessionId) {

        jdbc.update("INSERT INTO community_session (id, organization_id, community_id, status, scheduled_at, "
                + "notes, recording_url, has_time, created_at, updated_at, version) VALUES (?, 'default', 1, 'SCHEDULED', "
                + "TIMESTAMPTZ '2099-06-14T18:00:00Z', '', '', true, now(), now(), 0)", sessionId);
    }

    private ActionItemEntity.ActionItemEntityBuilder anyItem() {

        return ActionItemEntity.builder().organizationId("default").communityId(1L).title("Follow up").description("")
                .raisedByUserId("alice").outcome(ActionItemOutcome.OPEN);
    }

    private void checkpointRow(Long actionItemId, Long sessionId, CheckpointOutcome outcome) {

        actionItemCheckpointRepository.save(ActionItemCheckpointEntity.builder().organizationId("default")
                .actionItemId(actionItemId).sessionId(sessionId).outcome(outcome).decidedByUserId("alice")
                .decidedAt(Instant.now()).build());
    }
}
