package dev.axonia;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import dev.axonia.events.api.PastSessionsRequest;
import dev.axonia.events.api.Session;
import dev.axonia.events.application.ActionItemQuery;
import dev.axonia.events.application.ActionItemService;
import dev.axonia.events.application.SessionMapper;
import dev.axonia.events.application.SessionService;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.knowledge.api.Artifact;
import dev.axonia.knowledge.api.ArtifactFilter;
import dev.axonia.knowledge.application.ArtifactService;
import dev.axonia.shared.api.tenancy.OrganizationScope;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A page of a community's past sessions is the worked example: 20 sessions currently cost 686 connection checkouts
 * because {@code SessionService.past} carries no {@code @Transactional}, so every repository call Spring Data JDBC
 * makes on its behalf borrows and returns its own connection - each paying {@code OrganizationAwareDataSource}'s up to
 * four RLS round trips. A transactional read costs one.
 *
 * <p>
 * The call runs under a real JWT, not {@link OrganizationScope#runAs}: {@code events}' and {@code community}'s own
 * {@code OrganizationContext} ports read the caller's organization off the JWT {@code org} claim
 * ({@code JwtOrganizationContext} / {@code CurrentToken.organization()}), not off the thread-bound authentication
 * {@code OrganizationScope.runAs} installs for a JWT-less system action - only the RLS GUC itself is resolved
 * generically enough to accept either. {@link PerformanceSeed#sessions} still seeds through {@code OrganizationScope}
 * so the write side sees exactly the scope production writes under.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
@Import({CountingDataSourceRegistrar.class, PerformanceSeed.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class ReadPathQueryBudgetTest {

    private static final String READER_USER_ID = "perf-seed-reader";

    @Autowired
    private SessionService sessions;

    @Autowired
    private ArtifactService artifacts;

    @Autowired
    private ActionItemService actionItems;

    @Autowired
    private PerformanceSeed seed;

    @Autowired
    private SessionMapper sessionMapper;

    @Autowired
    private SessionStore sessionStore;

    private Long fullSessionId;

    private Long oneOffSessionId;

    @BeforeAll
    void seedTwentySessions() {

        seed.sessions(20, 4, 3);
    }

    @BeforeAll
    void seedFiftyArtifacts() {

        seed.artifacts(50);
    }

    @BeforeAll
    void seedOneFullyPopulatedSession() {

        fullSessionId = seed.oneFullyPopulatedSession();
    }

    @BeforeAll
    void seedOneOneOffSessionWithItsOwnZone() {

        oneOffSessionId = seed.oneOffSessionWithItsOwnZone();
    }

    @BeforeAll
    void seedThirtyActionItems() {

        seed.actionItems(30);
    }

    /**
     * {@code SessionEntity} is a four-level aggregate - sections, slots, then each slot's presenters and tags - and
     * Spring Data JDBC issues one query per parent row per collection, with no batching for nested aggregates and no
     * Single Query Loading support for them either. A page of 20 sessions of 4 sections of 3 slots therefore cost 587
     * statements, measured here. {@code SessionListReader} reads one query per level instead, keyed by the previous
     * level's ids, and this exact page now measures 14: the five level queries, the count beside them, the community
     * lookup, and the batched series / section-type / attendance lookups the fully populated row on this page needs. 18
     * leaves a little headroom without hiding a regression in this read.
     */
    @Test
    void aPageOfTwentySessionsCostsAFixedHandfulOfQueries() {

        var page = new AtomicReference<Page<Session>>();
        asOrganizationMember(() -> QueryBudget.assertWithin(1, 18, () -> page.set(sessions.past(
                PastSessionsRequest.builder().slug(PerformanceSeed.SLUG).pageable(PageRequest.of(0, 20)).build()))));

        // The budget alone cannot tell an empty read from a real one - a zero-row page also costs one connection -
        // so this proves the seeded sessions were the ones actually read, not that nothing was.
        assertThat(page.get().getContent()).hasSize(20);
        assertThat(page.get().getTotalElements()).isEqualTo(21);
    }

    /**
     * The assertion that actually pins the shape rather than a constant: a read whose cost grows with the page fails
     * here whatever number the budget above is set to.
     */
    @Test
    void thatCostDoesNotGrowWithPageSize() {

        asOrganizationMember(() -> {
            CountingDataSource.reset();
            var small = sessions.past(
                    PastSessionsRequest.builder().slug(PerformanceSeed.SLUG).pageable(PageRequest.of(0, 5)).build());
            var smallStatements = CountingDataSource.statements();
            CountingDataSource.reset();
            var large = sessions.past(
                    PastSessionsRequest.builder().slug(PerformanceSeed.SLUG).pageable(PageRequest.of(0, 20)).build());
            assertThat(small.getContent()).hasSize(5);
            assertThat(large.getContent()).hasSize(20);
            assertThat(CountingDataSource.statements()).isEqualTo(smallStatements);
        });
    }

    /**
     * The risk of this whole change, stated as an assertion: a hand-written read that quietly drops a field would pass
     * every budget above and change what a client sees. So one session with something in every column it can carry is
     * read both ways - through {@code SessionListReader}'s five queries, and through the aggregate the detail page
     * still loads - and the two are compared field by field, all the way down through sections, slots, presenters and
     * tags. Nothing is ignored: a difference anywhere fails here.
     */
    @Test
    void theBatchedReadReturnsWhatTheAggregateReadReturned() {

        asOrganizationMember(() -> {
            var viaAggregate = sessionMapper.toSession(
                    sessionStore.findByOrganizationIdAndId(PerformanceSeed.ORGANIZATION, fullSessionId).orElseThrow());
            var viaReader = sessions
                    .past(PastSessionsRequest.builder().slug(PerformanceSeed.SLUG).pageable(PageRequest.of(0, 50))
                            .build())
                    .getContent().stream().filter(s -> s.id().equals(fullSessionId)).findFirst().orElseThrow();

            assertThat(viaReader).usingRecursiveComparison().isEqualTo(viaAggregate);
        });
    }

    /** A one-off session keeps its own zone in the upcoming list exactly as its own detail read carries it. */
    @Test
    void theBatchedReadKeepsAOneOffSessionsOwnZone() {

        asOrganizationMember(() -> {
            var viaAggregate = sessionMapper.toSession(sessionStore
                    .findByOrganizationIdAndId(PerformanceSeed.ORGANIZATION, oneOffSessionId).orElseThrow());
            var viaReader = sessions.upcoming(PerformanceSeed.SLUG, PageRequest.of(0, 50)).getContent().stream()
                    .filter(s -> s.id().equals(oneOffSessionId)).findFirst().orElseThrow();

            assertThat(viaAggregate.timeZone()).isEqualTo("Europe/Zurich");
            assertThat(viaReader).usingRecursiveComparison().isEqualTo(viaAggregate);
        });
    }

    /**
     * {@code ArtifactSearchRepositoryImpl.search} used to throw its own ranked id query away and re-fetch each id one
     * at a time through {@code JdbcAggregateTemplate.findById} - 65 statements for this exact page, measured before
     * this fix.
     *
     * <p>
     * The fix is not {@code JdbcAggregateTemplate.findAllById}: it batches the root row through its own {@code IN}, but
     * still loads each row's {@code tags} collection with its own {@code WHERE artifact_id = ?} - measured as 20
     * separate statements for a 20-row page, not the single batched query its aggregate-loading machinery seems to
     * promise. So the hydration is hand-written instead, matching how the id and count queries above it are already
     * built: one query for the root rows and one for their tags, both keyed by {@code IN (:ids)}. That alone brought
     * this exact page down to 27, but 20 of those 27 were a second N+1 one file over: {@code ArtifactMapper.toArtifact}
     * resolved each artifact's type label one row at a time through
     * {@code ArtifactTypeRepository.findByOrganizationIdAndId}. Batched the same way - {@code toArtifacts} resolves
     * every distinct type id once via {@code findByOrganizationIdAndIdIn}, following {@code
     * SessionBatchLookups.sectionTypesOf}'s pattern - this exact page measures 8. 10 leaves a little headroom without
     * hiding a regression in this read.
     */
    @Test
    void aPageOfTwentyArtifactsTakesAHandfulOfQueries() {

        var page = new AtomicReference<Page<Artifact>>();
        asOrganizationMember(() -> QueryBudget.assertWithin(1, 10, () -> page
                .set(artifacts.search(PerformanceSeed.SLUG, ArtifactFilter.builder().build(), PageRequest.of(0, 20)))));

        assertThat(page.get().getContent()).hasSize(20);
        assertThat(page.get().getTotalElements()).isEqualTo(50);
    }

    /**
     * {@code ActionItemRepositoryCustomImpl.findFiltered} used to load its page through {@code JdbcAggregateTemplate},
     * which batches the root rows through its own {@code IN} but still loads each row's {@code tags}
     * {@code @MappedCollection} with its own {@code WHERE action_item_id = ?} - one statement per row on top of the
     * fixed cost, exactly the shape {@code aPageOfTwentyArtifactsTakesAHandfulOfQueries} above catches for artifacts.
     * This is the same proof for action items: a read whose cost grows with the page fails here whatever the page size,
     * which is what actually catches that regression and keeps it caught.
     *
     * <p>
     * The fixed cost is higher than the artifact read's own 8 because {@code ActionItemService.list} also runs
     * {@code CommunityAccess.communityIdForSlug} and {@code TopicAuthorizer.canManageInCommunity}'s membership /
     * organizer checks before the repository is ever called, and because {@code CheckpointResolver.resolveAll} pays for
     * the page's distinct pinned sessions, the community's soonest upcoming session that every late-bound row shares,
     * the batched zone of every series those sessions belong to, and the {@code sections} collection Spring Data JDBC
     * loads for each session entity the two session reads return. This exact page measures 18. 22 leaves a little
     * headroom without hiding a regression in this read.
     *
     * <p>
     * {@link PerformanceSeed#actionItems} pins every other item and leaves the rest late-bound on purpose, so both arms
     * of that resolution are on every page whatever its size.
     */
    @Test
    void aPageOfActionItemsCostsAFixedHandfulOfQueriesAndDoesNotGrowWithPageSize() {

        asOrganizationMember(() -> {
            CountingDataSource.reset();
            var small = actionItems.list(PerformanceSeed.SLUG, new ActionItemQuery(null, null, null),
                    PageRequest.of(0, 5));
            var smallStatements = CountingDataSource.statements();
            CountingDataSource.reset();
            var large = actionItems.list(PerformanceSeed.SLUG, new ActionItemQuery(null, null, null),
                    PageRequest.of(0, 20));
            var largeStatements = CountingDataSource.statements();
            assertThat(small.getContent()).hasSize(5);
            assertThat(large.getContent()).hasSize(20);
            assertThat(large.getTotalElements()).isEqualTo(30);
            // isPositive pins that CountingDataSource is actually wrapping the dataSource bean in this slice - an
            // equality assertion alone would pass vacuously at 0 == 0 if it were not.
            assertThat(smallStatements).as("statements for a 5-row page").isPositive().isLessThanOrEqualTo(22);
            assertThat(largeStatements).as("statements for a 20-row page must not exceed the 5-row page's")
                    .isEqualTo(smallStatements);
        });
    }

    /**
     * {@code SessionService.past} reads no {@link dev.axonia.events.application.port.out.CurrentUser}, so the JWT only
     * has to carry the {@code org} claim {@code JwtOrganizationContext} resolves the organization from - the same claim
     * {@code community}'s own port reads when {@code CommunityAccess.communityIdForSlug} looks the seeded community up.
     */
    private void asOrganizationMember(Runnable action) {

        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject(READER_USER_ID)
                .claim("org", PerformanceSeed.ORGANIZATION).build();
        var context = SecurityContextHolder.getContext();
        var previous = context.getAuthentication();
        context.setAuthentication(new JwtAuthenticationToken(jwt));
        try {
            action.run();
        } finally {
            context.setAuthentication(previous);
        }
    }
}
